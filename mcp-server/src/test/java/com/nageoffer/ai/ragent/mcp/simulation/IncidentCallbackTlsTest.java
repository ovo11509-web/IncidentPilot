package com.nageoffer.ai.ragent.mcp.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nageoffer.ai.ragent.mcp.McpServerApplication;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;

class IncidentCallbackTlsTest {
    @Test
    void callbackUsesTlsAndIndependentIdentityAtNonExemptLocalAddress() throws Exception {
        Path storePath = Files.createTempFile(Path.of("target"), "callback-tls-", ".p12");
        Files.delete(storePath);
        String password = UUID.randomUUID().toString();
        String callbackToken = UUID.randomUUID().toString() + UUID.randomUUID();
        String ticket = "a".repeat(64);
        HttpsServer server = null;
        try {
            Path keytool = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
            var keytoolProcess = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "test",
                    "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12", "-keystore",
                    storePath.toAbsolutePath().toString(), "-storepass", password, "-keypass", password,
                    "-dname", "CN=callback-local", "-ext", "SAN=IP:127.0.0.2",
                    "-validity", "1", "-noprompt").redirectErrorStream(true).start();
            assertEquals(0, keytoolProcess.waitFor());
            var store = KeyStore.getInstance("PKCS12");
            try (var stream = Files.newInputStream(storePath)) {
                store.load(stream, password.toCharArray());
            }
            var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, password.toCharArray());
            var serverTls = SSLContext.getInstance("TLS");
            serverTls.init(keys.getKeyManagers(), null, new SecureRandom());
            var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            var clientTls = SSLContext.getInstance("TLS");
            clientTls.init(null, trust.getTrustManagers(), null);

            server = HttpsServer.create(new InetSocketAddress("127.0.0.2", 0), 0);
            server.setHttpsConfigurator(new HttpsConfigurator(serverTls));
            var consumed = new AtomicInteger();
            var receipts = new AtomicInteger();
            server.createContext("/api/ragent/incident/internal/v1/", exchange -> {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    assertEquals("Bearer " + callbackToken,
                            exchange.getRequestHeaders().getFirst("Authorization"));
                    String response;
                    if (exchange.getRequestURI().getPath().endsWith("/action-tickets/consume")) {
                        assertTrue(body.contains(ticket));
                        consumed.incrementAndGet();
                        response = "{\"code\":\"SUCCESS\",\"data\":{\"decision\":\"GRANTED\","
                                + "\"maxExecutionDurationMs\":120000}}";
                    } else {
                        receipts.incrementAndGet();
                        response = "{\"code\":\"SUCCESS\"}";
                    }
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            String secure = "https://127.0.0.2:" + server.getAddress().getPort() + "/api/ragent";
            var client = HttpClient.newBuilder().sslContext(clientTls).build();
            var callback = new IncidentCallbackClient(client, Map.of(
                    "INCIDENT_MCP_CALLBACK_BASE_URL", secure,
                    "INCIDENT_MCP_CALLBACK_TOKEN", callbackToken));
            assertEquals("GRANTED", callback.consume(Map.of("ticket", ticket)).decision());
            callback.receipt(Map.of("receiptId", "1"));
            assertEquals(1, consumed.get());
            assertEquals(1, receipts.get());
            var insecure = new IncidentCallbackClient(client, Map.of(
                    "INCIDENT_MCP_CALLBACK_BASE_URL", secure.replace("https:", "http:"),
                    "INCIDENT_MCP_CALLBACK_TOKEN", callbackToken));
            assertThrows(IllegalStateException.class, () -> insecure.consume(Map.of("ticket", ticket)));
            assertEquals(1, consumed.get());

            try (var fixture = new SimulationTestFixture();
                    var app = new SpringApplicationBuilder(McpServerApplication.class,
                            ProtectedSimulationRouteTest.TestIdentity.class)
                            .run("--server.address=127.0.0.2", "--server.port=0", "--server.ssl.enabled=true",
                                    "--server.ssl.key-store=" + storePath.toUri(),
                                    "--server.ssl.key-store-type=PKCS12",
                                    "--server.ssl.key-store-password=" + password,
                                    "--server.ssl.key-alias=test",
                                    "--ragent.bit.datasource.url=" + fixture.jdbcUrl(),
                                    "--ragent.bit.datasource.username=" + fixture.username(),
                                    "--ragent.bit.datasource.password=" + fixture.password())) {
                int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
                String base = "https://127.0.0.2:" + port;
                ProtectedServiceAuthentication.requireSafeEndpoint(java.net.URI.create(base + "/mcp/incident"));
                try (var mcp = McpClient.sync(HttpClientStreamableHttpTransport.builder(base)
                        .endpoint("/mcp/incident")
                        .customizeClient(builder -> builder.sslContext(clientTls))
                        .customizeRequest(request -> request.header("Authorization",
                                "Bearer isolated-test-outbound-token-12345"))
                        .build()).build()) {
                    mcp.initialize();
                    assertEquals("simulation.set-state", mcp.listTools().tools().get(0).name());
                    assertTrue(mcp.callTool(new CallToolRequest("simulation.set-state",
                            Map.of("targetId", SimulationTestFixture.TARGET_ID,
                                    "desiredState", "PAUSED"), Map.of())).isError());
                }
                assertEquals(SimulationTestFixture.INITIAL_STATE, fixture.state());
                assertEquals(0, fixture.applicationCount());
            }
        } finally {
            if (server != null) server.stop(0);
            Files.deleteIfExists(storePath);
        }
    }
}
