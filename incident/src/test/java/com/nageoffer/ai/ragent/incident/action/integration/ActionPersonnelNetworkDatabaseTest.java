/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionClosureInvalidator;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionProposalService;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.web.ActionController;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.core.service.EpisodePartitioner;
import com.nageoffer.ai.ragent.incident.core.service.IncidentStateMachine;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import com.nageoffer.ai.ragent.incident.core.web.IncidentController;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;

/** Real loopback HTTP, Servlet dispatch, personnel authorization and isolated PostgreSQL. */
class ActionPersonnelNetworkDatabaseTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ActionDbFixture db;

    @Test
    void rejectsExpiredAndForgedRequestsWithoutStartingExecution() throws Exception {
        try (var fixture = new ActionDbFixture()) {
            db = fixture;
            db.seedIncident("100");
            try (var app = new SpringApplicationBuilder(TestApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .properties("spring.main.banner-mode=off")
                    .run("--server.address=127.0.0.1", "--server.port=0")) {
                int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
                assertTrue(port > 0);
                String tokenName = StpUtil.getTokenName();
                String token = StpUtil.createLoginSession("42");
                var http = HttpClient.newHttpClient();
                var base = URI.create("http://127.0.0.1:" + port);

                var withoutSession = post(http, base, "/incident/v1/incidents/100/actions",
                        proposal("unauthorized"), null, null);
                assertTrue(withoutSession.statusCode() == 401 || withoutSession.statusCode() == 403);
                assertEquals(0, count("t_incident_action"));

                String rejected = actionId(post(http, base, "/incident/v1/incidents/100/actions",
                        proposal("rejected"), tokenName, token));
                assertEquals(200, post(http, base, "/incident/v1/actions/" + rejected + "/decision",
                        "{\"decision\":\"REJECT\",\"reason\":\"unsafe\",\"expectedVersion\":0}",
                        tokenName, token).statusCode());
                assertEquals(409, post(http, base, "/incident/v1/actions/" + rejected + "/decision",
                        "{\"decision\":\"APPROVE\",\"expectedVersion\":1}",
                        tokenName, token).statusCode());
                var repeated = post(http, base, "/incident/v1/incidents/100/actions",
                        proposal("rejected"), tokenName, token);
                assertEquals(200, repeated.statusCode());
                assertEquals(rejected, JSON.readTree(repeated.body()).path("data").path("actionId").asText());

                String expired = actionId(post(http, base, "/incident/v1/incidents/100/actions",
                        proposal("expired"), tokenName, token));
                db.sql("UPDATE t_incident_action SET approval_due_at=now()-interval '1 second' WHERE id='"
                        + expired + "'");
                db.sql("UPDATE t_incident_action_approval SET approval_due_at=now()-interval '1 second' "
                        + "WHERE action_id='" + expired + "'");
                assertEquals(409, post(http, base, "/incident/v1/actions/" + expired + "/decision",
                        "{\"decision\":\"APPROVE\",\"expectedVersion\":0}",
                        tokenName, token).statusCode());
                assertEquals("EXPIRED", db.actions.findById(expired).getState());

                for (String field : new String[] {"actionId", "incidentId", "executionId", "executionKey",
                        "requestId", "ticket", "bindingVersion"}) {
                    String forged = proposal("forged-" + field).replace("\"toolName\"",
                            "\"" + field + "\":\"forged\",\"toolName\"");
                    assertEquals(422, post(http, base, "/incident/v1/incidents/100/actions",
                            forged, tokenName, token).statusCode());
                }
                for (String body : new String[] {
                        proposal("wrong-tool").replace("simulation.set-state", "other.write"),
                        proposal("wrong-version").replace("\"toolVersion\":\"1\"", "\"toolVersion\":\"2\""),
                        proposal("wrong-target").replace("\"targetId\":\"demo\"", "\"targetId\":\"other\"")}) {
                    assertEquals(422, post(http, base, "/incident/v1/incidents/100/actions",
                            body, tokenName, token).statusCode());
                }
                assertEquals(409, post(http, base, "/incident/v1/incidents/100/actions",
                        proposal("rejected").replace("PAUSED", "READY"),
                        tokenName, token).statusCode());

                String closing = actionId(post(http, base, "/incident/v1/incidents/100/actions",
                        proposal("closing"), tokenName, token));
                db.sql("UPDATE t_incident SET lifecycle_state='RESOLVED', "
                        + "lifecycle_sealed_at=clock_timestamp(), aggregation_slot_active=false WHERE id='100'");
                assertEquals(200, post(http, base, "/incident/v1/incidents/100/transitions",
                        "{\"expectedState\":\"RESOLVED\",\"expectedVersion\":0,"
                                + "\"targetState\":\"CLOSED\",\"reason\":\"resolved\"}",
                        tokenName, token).statusCode());
                assertEquals(409, post(http, base, "/incident/v1/actions/" + closing + "/decision",
                        "{\"decision\":\"APPROVE\",\"expectedVersion\":0}",
                        tokenName, token).statusCode());
                assertEquals("EXPIRED", db.actions.findById(closing).getState());
                assertEquals(0, count("t_incident_action_execution"));
            } finally {
                db = null;
            }
        }
    }

    @Test
    void concurrentApprovalAndCloseNeverStartAnExecution() throws Exception {
        try (var fixture = new ActionDbFixture()) {
            db = fixture;
            db.seedIncident("100");
            try (var app = new SpringApplicationBuilder(TestApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .properties("spring.main.banner-mode=off")
                    .run("--server.address=127.0.0.1", "--server.port=0")) {
                int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
                String tokenName = StpUtil.getTokenName();
                String token = StpUtil.createLoginSession("42");
                var http = HttpClient.newHttpClient();
                var base = URI.create("http://127.0.0.1:" + port);
                String actionId = actionId(post(http, base, "/incident/v1/incidents/100/actions",
                        proposal("close-race"), tokenName, token));
                db.sql("UPDATE t_incident SET lifecycle_state='RESOLVED', "
                        + "lifecycle_sealed_at=clock_timestamp(), aggregation_slot_active=false WHERE id='100'");
                var start = new CountDownLatch(1);
                var approve = CompletableFuture.supplyAsync(() -> racingPost(start, http, base,
                        "/incident/v1/actions/" + actionId + "/decision",
                        "{\"decision\":\"APPROVE\",\"expectedVersion\":0}", tokenName, token));
                var close = CompletableFuture.supplyAsync(() -> racingPost(start, http, base,
                        "/incident/v1/incidents/100/transitions",
                        "{\"expectedState\":\"RESOLVED\",\"expectedVersion\":0,"
                                + "\"targetState\":\"CLOSED\",\"reason\":\"resolved\"}",
                        tokenName, token));
                start.countDown();
                int approvalStatus = approve.join();
                assertTrue(approvalStatus == 200 || approvalStatus == 409);
                assertEquals(200, close.join());
                assertEquals("EXPIRED", db.actions.findById(actionId).getState());
                assertEquals(0, count("t_incident_action_execution"));
            } finally {
                db = null;
            }
        }
    }

    @Test
    void servesPersonnelRequestOverIsolatedHttps() throws Exception {
        servesPersonnelRequestOverIsolatedHttps("127.0.0.1");
    }

    @Test
    void nonExemptLoopbackAliasRequiresTlsAndServesIsolatedHttps() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ActionServiceAuthentication.requireSafeEndpoint(
                URI.create("http://127.0.0.2:9099/mcp/incident")));
        ActionServiceAuthentication.requireSafeEndpoint(URI.create("https://127.0.0.2:9099/mcp/incident"));
        servesPersonnelRequestOverIsolatedHttps("127.0.0.2");
    }

    private static void servesPersonnelRequestOverIsolatedHttps(String address) throws Exception {
        Path keystorePath = Files.createTempFile(Path.of("target"), "action-https-", ".p12");
        Files.delete(keystorePath);
        String password = UUID.randomUUID().toString();
        try {
            Path keytool = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
            var keytoolProcess = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "test",
                    "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12", "-keystore",
                    keystorePath.toAbsolutePath().toString(), "-storepass", password, "-keypass", password,
                    "-dname", "CN=localhost", "-ext", "SAN=DNS:localhost,IP:127.0.0.1,IP:127.0.0.2",
                    "-validity", "1", "-noprompt").redirectErrorStream(true).start();
            assertEquals(0, keytoolProcess.waitFor(), "temporary test certificate generation failed");
            try (var fixture = new ActionDbFixture()) {
                db = fixture;
                db.seedIncident("100");
                try (var app = new SpringApplicationBuilder(TestApplication.class)
                        .web(WebApplicationType.SERVLET)
                        .properties("spring.main.banner-mode=off")
                        .run("--server.address=" + address, "--server.port=0", "--server.ssl.enabled=true",
                                "--server.ssl.key-store=" + keystorePath.toUri(),
                                "--server.ssl.key-store-type=PKCS12",
                                "--server.ssl.key-store-password=" + password,
                                "--server.ssl.key-alias=test")) {
                    var store = KeyStore.getInstance("PKCS12");
                    try (var stream = Files.newInputStream(keystorePath)) {
                        store.load(stream, password.toCharArray());
                    }
                    var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    trust.init(store);
                    var tls = SSLContext.getInstance("TLS");
                    tls.init(null, trust.getTrustManagers(), null);
                    var http = HttpClient.newBuilder().sslContext(tls).build();
                    int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
                    String tokenName = StpUtil.getTokenName();
                    String token = StpUtil.createLoginSession("42");
                    var base = URI.create("https://" + address + ":" + port);
                    assertEquals(201, post(http, base, "/incident/v1/incidents/100/actions",
                            proposal("tls-local"), tokenName, token).statusCode());
                    assertEquals(0, count("t_incident_action_execution"));
                } finally {
                    db = null;
                }
            }
        } finally {
            Files.deleteIfExists(keystorePath);
        }
    }

    private static int racingPost(CountDownLatch start, HttpClient http, URI base, String path,
            String body, String tokenName, String token) {
        try {
            start.await();
            return post(http, base, path, body, tokenName, token).statusCode();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static HttpResponse<String> post(HttpClient http, URI base, String path, String body,
            String tokenName, String token) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/json");
        if (token != null) request.header(tokenName, token);
        var response = http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (token != null) assertTrue(!response.body().contains(token), "session token in HTTP response");
        assertTrue(!response.body().contains("\"parameters\""), "raw parameters in HTTP response");
        assertTrue(!response.body().contains("executionKey"), "execution key in HTTP response");
        return response;
    }

    private static String actionId(HttpResponse<String> response) throws Exception {
        assertEquals(201, response.statusCode(), response.body());
        JsonNode data = JSON.readTree(response.body()).path("data");
        String id = data.path("actionId").asText();
        assertTrue(id.matches("[0-9]+"));
        return id;
    }

    private static String proposal(String requestId) {
        return "{\"sourceRequestId\":\"" + requestId + "\",\"toolName\":\"simulation.set-state\","
                + "\"toolVersion\":\"1\",\"targetId\":\"demo\","
                + "\"parameters\":{\"desiredState\":\"PAUSED\"}}";
    }

    private static long count(String table) throws SQLException {
        try (var connection = db.openConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class,
            excludeName = {"cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate",
                    "org.redisson.spring.starter.RedissonAutoConfigurationV4"})
    static class TestApplication {
        @Bean
        com.nageoffer.ai.ragent.incident.core.service.IncidentQueryService incidentQueryService() {
            return new com.nageoffer.ai.ragent.incident.core.service.IncidentQueryService(db.incidents, db.alerts);
        }

        @Bean
        ActionController actions() {
            return new ActionController(new ActionProposalService(db.actions, db.approvals, db.guard,
                    db.actionTimeline, db.alerts, db.properties),
                    new ActionDecisionService(db.actions, db.approvals, db.guard,
                            db.actionTimeline, db.actionOutbox, db.properties, db.alerts), db.approvals);
        }

        @Bean
        IncidentController incidents() {
            var transitions = new IncidentTransitionService(db.incidents, db.alerts, db.clock,
                    new IncidentStateMachine(), db.incidentTimeline, db.transactions);
            transitions.setActionClosureInvalidator(new ActionClosureInvalidator(db.actions, db.approvals,
                    db.executions, db.tickets, db.incidents, db.actionTimeline));
            return new IncidentController(db.incidents, db.alerts, db.timeline, transitions,
                    new EpisodePartitioner());
        }

        @Bean
        IncidentErrorAdvice errors() {
            return new IncidentErrorAdvice();
        }

        @Bean
        StpInterface testRoles() {
            return new StpInterface() {
                @Override
                public List<String> getPermissionList(Object loginId, String loginType) {
                    return List.of();
                }

                @Override
                public List<String> getRoleList(Object loginId, String loginType) {
                    return "42".equals(loginId.toString()) ? List.of("admin") : List.of();
                }
            };
        }
    }
}
