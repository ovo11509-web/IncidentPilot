package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionProposalService;
import com.nageoffer.ai.ragent.incident.action.service.ActionQueryService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.web.ActionInternalController;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker;
import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer;
import com.nageoffer.ai.ragent.incident.alert.service.AlertFingerprint;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReceiveService;
import com.nageoffer.ai.ragent.incident.alert.worker.AlertRetryPolicy;
import com.nageoffer.ai.ragent.incident.alert.worker.AlertWorker;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** One disposable Spec A to Spec B path, excluding the not-yet-built Runbook diagnosis. */
class AlertToSimulationE2ETest {
    private static final String OUTBOUND = "isolated-golden-outbound-token-123456789";
    private static final String CALLBACK = "isolated-golden-callback-token-123456789";

    @Test
    void twoAlertsAggregateThenRejectedAndApprovedActionsKeepOneSimulationEffect() throws Exception {
        try (var db = new ActionDbFixture(); var bit = new PostgreSQLContainer("postgres:16-alpine");
                var login = Mockito.mockStatic(StpUtil.class)) {
            bit.start();
            try (var connection = DriverManager.getConnection(bit.getJdbcUrl(), bit.getUsername(), bit.getPassword());
                    var statement = connection.createStatement();
                    var schema = getClass().getClassLoader().getResourceAsStream("db/bit-schema.sql")) {
                assertNotNull(schema);
                statement.execute(new String(schema.readAllBytes(), StandardCharsets.UTF_8));
                statement.executeUpdate("INSERT INTO simulation_target(target_id,state) VALUES('demo','READY')");
            }
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            var canonicalizer = new AlertV1Canonicalizer(db.incidentProperties);
            var receiver = new AlertReceiveService(db.alerts, db.audit, db.clock,
                    new AlertFingerprint(), db.transactions);
            String occurredAt = OffsetDateTime.now(ZoneOffset.UTC).toString();
            String firstId = receive(receiver, canonicalizer, "golden-alert-1", occurredAt);
            String secondId = receive(receiver, canonicalizer, "golden-alert-2", occurredAt);
            var retry = new AlertRetryPolicy(db.alerts, db.audit, db.incidents,
                    db.incidentProperties, db.transactions);
            new AlertWorker(db.alerts, db.aggregation, db.incidentProperties, retry).processDue();
            String incidentId = db.alerts.findById(firstId).getIncidentId();
            assertNotNull(incidentId);
            assertEquals(incidentId, db.alerts.findById(secondId).getIncidentId());
            assertEquals(2, db.incidents.findById(incidentId).getAlertCount());

            var proposal = new ActionProposalService(db.actions, db.approvals, db.guard,
                    db.actionTimeline, db.alerts, db.properties);
            var rejected = proposal.proposeUser(new ActionProposalService.UserProposal(incidentId,
                    "golden-reject", null, "simulation.set-state", "1", "demo",
                    "{\"desiredState\":\"PAUSED\"}")).action();
            var decisions = new ActionDecisionService(db.actions, db.approvals, db.guard,
                    db.actionTimeline, db.actionOutbox, db.properties, db.alerts);
            decisions.decide(rejected.getId(), 0, "REJECT", "unsafe now", "golden-reject");
            assertEquals("APPROVAL_REJECTED", db.actions.findById(rejected.getId()).getState());
            assertEquals(0, count(db, "t_incident_action_execution"));
            assertEquals(0, ProtectedSimulationE2ETest.applicationCount(bit));

            var approved = proposal.proposeUser(new ActionProposalService.UserProposal(incidentId,
                    "golden-approve", rejected.getId(), "simulation.set-state", "1", "demo",
                    "{\"desiredState\":\"PAUSED\"}")).action();
            decisions.decide(approved.getId(), 0, "APPROVE", null, "golden-approve");
            var claim = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim(approved.getId());
            var auth = mock(ActionServiceAuthentication.class);
            when(auth.isAuthenticated("Bearer " + CALLBACK)).thenReturn(true);
            var controller = new ActionInternalController(new ActionTicketConsumptionService(
                    db.actions, db.approvals, db.executions, db.tickets, db.guard,
                    db.actionTimeline, db.alerts, auth), new ActionReceiptService(db.actions,
                    db.executions, db.receipts, db.guard, db.actionTimeline,
                    db.actionOutbox, db.alerts, auth));
            var callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            callbackServer.createContext("/incident/internal/v1/action-tickets/consume", exchange ->
                    ProtectedSimulationE2ETest.respond(exchange, body -> controller.consume(body,
                            exchange.getRequestHeaders().getFirst("Authorization"), null).getBody()));
            callbackServer.createContext("/incident/internal/v1/action-receipts", exchange ->
                    ProtectedSimulationE2ETest.respond(exchange, body -> controller.receipt(body,
                            exchange.getRequestHeaders().getFirst("Authorization")).getBody()));
            callbackServer.start();
            Path output = Path.of("target", "alert-to-simulation-process.log").toAbsolutePath();
            Files.createDirectories(output.getParent());
            Process child = null;
            try {
                int mcpPort = ProtectedSimulationE2ETest.availablePort();
                String classpath = System.getProperty("surefire.test.class.path");
                assertNotNull(classpath);
                var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe")
                        .toString(), "-cp", classpath, "com.nageoffer.ai.ragent.mcp.McpServerApplication",
                        "--server.port=" + mcpPort,
                        "--spring.autoconfigure.exclude=org.redisson.spring.starter.RedissonAutoConfigurationV4",
                        "--ragent.bit.datasource.url=" + bit.getJdbcUrl(),
                        "--ragent.bit.datasource.username=" + bit.getUsername(),
                        "--ragent.bit.datasource.password=" + bit.getPassword());
                command.environment().put("INCIDENT_MCP_OUTBOUND_TOKEN", OUTBOUND);
                command.environment().put("INCIDENT_MCP_CALLBACK_TOKEN", CALLBACK);
                command.environment().put("INCIDENT_MCP_CALLBACK_BASE_URL", "http://127.0.0.1:"
                        + callbackServer.getAddress().getPort());
                command.redirectErrorStream(true).redirectOutput(output.toFile());
                child = command.start();
                String base = "http://127.0.0.1:" + mcpPort;
                ProtectedSimulationE2ETest.awaitServer(child, base);
                try (var mcp = McpClient.sync(HttpClientStreamableHttpTransport.builder(base)
                        .endpoint("/mcp/incident")
                        .customizeRequest(request -> request.header("Authorization", "Bearer " + OUTBOUND))
                        .build()).build()) {
                    mcp.initialize();
                    var metadata = Map.<String, Object>of("actionId", claim.actionId(),
                            "incidentId", claim.incidentId(), "executionId", claim.executionId(),
                            "executionKey", claim.executionKey(), "requestId", claim.requestId(),
                            "ticket", claim.ticket(), "bindingVersion", claim.bindingVersion());
                    var request = new CallToolRequest("simulation.set-state",
                            Map.of("targetId", "demo", "desiredState", "PAUSED"), metadata);
                    assertFalse(mcp.callTool(request).isError());
                    assertTrue(mcp.callTool(request).isError());
                }
                assertEquals("SUCCEEDED", db.actions.findById(approved.getId()).getState());
                assertEquals(1, db.receipts.findByExecutionId(claim.executionId()).size());
                assertEquals(1, ProtectedSimulationE2ETest.applicationCount(bit));
                assertEquals("PAUSED", ProtectedSimulationE2ETest.targetState(bit));
                assertEquals(1, count(db, "t_incident_action_execution"));
                assertTrue(db.timeline.findByIncidentId(incidentId).size() >= 8);
            } finally {
                if (child != null) {
                    child.destroy();
                    if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly();
                }
                callbackServer.stop(0);
                String log = Files.readString(output);
                assertFalse(log.contains(claim.ticket()));
                assertFalse(log.contains(OUTBOUND));
                assertFalse(log.contains(CALLBACK));
            }
            var restoredQuery = new ActionQueryService(db.actions, db.approvals, db.executions,
                    db.tickets, db.receipts, db.incidents, db.timeline, db.transactions);
            assertEquals("SUCCEEDED", restoredQuery.get(approved.getId()).get("state"));
            assertEquals(2, restoredQuery.list(incidentId).size());
        }
    }

    private static String receive(AlertReceiveService receiver, AlertV1Canonicalizer canonicalizer,
            String externalId, String occurredAt) {
        String json = "{\"externalAlertId\":\"" + externalId + "\",\"eventType\":\"FIRING\","
                + "\"occurredAt\":\"" + occurredAt + "\",\"ruleId\":\"cpu\","
                + "\"title\":\"safe probe\",\"severity\":\"HIGH\",\"environment\":\"test\","
                + "\"service\":\"api\",\"resource\":{\"type\":\"host\",\"id\":\"demo-host\"}}";
        var canonical = canonicalizer.canonicalize(canonicalizer.parseStructure(
                json.getBytes(StandardCharsets.UTF_8)));
        return receiver.receive("source", canonical, "receive-" + externalId).alertId();
    }

    private static long count(ActionDbFixture db, String table) throws Exception {
        try (var connection = db.openConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
