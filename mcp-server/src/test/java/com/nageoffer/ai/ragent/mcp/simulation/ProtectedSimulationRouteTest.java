package com.nageoffer.ai.ragent.mcp.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

import com.nageoffer.ai.ragent.mcp.config.McpServerConfig;
import com.nageoffer.ai.ragent.mcp.config.ProtectedSimulationMcpConfig;
import com.nageoffer.ai.ragent.mcp.McpServerApplication;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

class ProtectedSimulationRouteTest {
    private static final Set<String> KNOWN_WRITES = Set.of("apply_after_sale", "asset_renewal_submit",
            "set_cart_item", "leave_submit", "meeting_room_book", "change_address",
            "cancel_order", "create_order", "pay_order", "submit_ticket");

    @Test
    void ordinaryRegistryExcludesEveryKnownWriteAndUnknownEvenWithReadOnlyClaim() {
        var offered = new ArrayList<McpServerFeatures.SyncToolSpecification>();
        offered.add(spec("current_date", true));
        KNOWN_WRITES.forEach(name -> offered.add(spec(name, false)));
        offered.add(spec("simulation.set-state", false));
        offered.add(spec("new_unreviewed_tool", true));
        var allowed = McpServerConfig.ordinaryTools(offered);
        var names = allowed.stream().map(item -> item.tool().name()).toList();
        assertEquals(List.of("current_date"), names);
        assertTrue(KNOWN_WRITES.stream().noneMatch(names::contains));
        assertFalse(names.contains("simulation.set-state"));
    }

    @Test
    void protectedTransportRejectsUnauthenticatedRequestsBeforeMcpDispatch() throws Exception {
        var auth = mock(ProtectedServiceAuthentication.class);
        var filter = new ProtectedSimulationMcpConfig().protectedMcpAuthenticationFilter(auth).getFilter();
        var request = new MockHttpServletRequest("POST", "/mcp/incident");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertEquals(null, chain.getRequest());
        when(auth.isAuthenticated("Bearer valid")).thenReturn(true);
        request.addHeader("Authorization", "Bearer valid");
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertEquals(request, chain.getRequest());
    }

    @Test
    void ticketDenialAndTamperingHaveZeroApplications() {
        var executor = mock(SimulationToolExecutor.class);
        var callbacks = mock(IncidentCallbackClient.class);
        when(executor.isRegisteredTarget("demo")).thenReturn(true);
        var tool = new ProtectedSimulationTool(executor, callbacks);
        var meta = Map.<String, Object>of("actionId", "200", "incidentId", "100", "executionId", "300",
                "executionKey", "key", "requestId", "dispatch-300", "ticket", "opaque",
                "bindingVersion", "action-binding-v1");
        var args = Map.<String, Object>of("targetId", "demo", "desiredState", "PAUSED");
        assertTrue(tool.invoke(new CallToolRequest("simulation.set-state", args, Map.of())).isError());
        when(callbacks.consume(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new IncidentCallbackClient.ConsumeGrant("DUPLICATE", -1));
        assertTrue(tool.invoke(new CallToolRequest("simulation.set-state", args, meta)).isError());
        verify(executor, never()).apply(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void grantedTicketProducesOneApplicationAndSafeStructuredReceipt() {
        var executor = mock(SimulationToolExecutor.class);
        var callbacks = mock(IncidentCallbackClient.class);
        when(executor.isRegisteredTarget("demo")).thenReturn(true);
        when(callbacks.consume(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new IncidentCallbackClient.ConsumeGrant("GRANTED", 120000));
        var tool = new ProtectedSimulationTool(executor, callbacks);
        var meta = Map.<String, Object>of("actionId", "200", "incidentId", "100", "executionId", "300",
                "executionKey", "key", "requestId", "dispatch-300", "ticket", "opaque",
                "bindingVersion", "action-binding-v1");
        var args = Map.<String, Object>of("targetId", "demo", "desiredState", "PAUSED");
        var result = tool.invoke(new CallToolRequest("simulation.set-state", args, meta));
        assertFalse(result.isError());
        assertFalse(result.structuredContent().toString().contains("opaque"));
        verify(executor).apply("key", "demo", "PAUSED", 120000);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> submitted = ArgumentCaptor.forClass(Map.class);
        verify(callbacks).receipt(submitted.capture());
        assertEquals(submitted.getValue(), result.structuredContent());
    }

    @Test
    void grantedShorterRegisteredDeadlineBoundsExecutionWithoutReplay() {
        var executor = mock(SimulationToolExecutor.class);
        var callbacks = mock(IncidentCallbackClient.class);
        when(executor.isRegisteredTarget("demo")).thenReturn(true);
        when(callbacks.consume(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new IncidentCallbackClient.ConsumeGrant("GRANTED", 50));
        when(executor.apply("key", "demo", "PAUSED", 50)).thenAnswer(invocation -> {
            Thread.sleep(200);
            return new SimulationToolExecutor.Application("APPLIED", "READY", "PAUSED");
        });
        var tool = new ProtectedSimulationTool(executor, callbacks,
                new SimulationTimeoutEvaluator(java.time.Duration.ofSeconds(1)));
        var meta = Map.<String, Object>of("actionId", "200", "incidentId", "100", "executionId", "300",
                "executionKey", "key", "requestId", "dispatch-300", "ticket", "opaque",
                "bindingVersion", "action-binding-v1");
        var result = tool.invoke(new CallToolRequest("simulation.set-state",
                Map.of("targetId", "demo", "desiredState", "PAUSED"), meta));
        @SuppressWarnings("unchecked")
        var receipt = (Map<String, Object>) result.structuredContent();
        assertEquals("UNKNOWN", receipt.get("resultStatus"));
        assertEquals("TIMEOUT_UNPROVEN", receipt.get("resultCode"));
        verify(executor).apply("key", "demo", "PAUSED", 50);
    }

    @Test
    void liveHttpRegistriesDenyOldWritesAndRequireProtectedIdentity() throws Exception {
        try (var fixture = new SimulationTestFixture();
                var app = new SpringApplicationBuilder(McpServerApplication.class, TestIdentity.class)
                        .run("--server.port=0", "--ragent.bit.datasource.url=" + fixture.jdbcUrl(),
                                "--ragent.bit.datasource.username=" + fixture.username(),
                                "--ragent.bit.datasource.password=" + fixture.password())) {
            int port = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
            String base = "http://127.0.0.1:" + port;
            try (var ordinary = McpClient.sync(HttpClientStreamableHttpTransport.builder(base)
                            .build()).build()) {
                ordinary.initialize();
                var names = ordinary.listTools().tools().stream().map(item -> item.name()).toList();
                assertTrue(names.contains("current_date"));
                assertTrue(KNOWN_WRITES.stream().noneMatch(names::contains));
                assertFalse(names.contains("simulation.set-state"));
                assertFalse(ordinary.callTool(new CallToolRequest("current_date", Map.of())).isError());
                for (String name : KNOWN_WRITES) {
                    denyDirect(ordinary, name);
                }
                denyDirect(ordinary, "simulation.set-state");
                denyDirect(ordinary, "new_unreviewed_tool");
            }
            try (var anonymous = McpClient.sync(HttpClientStreamableHttpTransport.builder(base)
                    .endpoint("/mcp/incident").build()).build()) {
                org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, anonymous::initialize);
            }
            try (var protectedClient = McpClient.sync(HttpClientStreamableHttpTransport.builder(base)
                    .endpoint("/mcp/incident").customizeRequest(
                            request -> request.header("Authorization", "Bearer isolated-test-outbound-token-12345"))
                    .build()).build()) {
                protectedClient.initialize();
                assertEquals(List.of("simulation.set-state"), protectedClient.listTools().tools().stream()
                        .map(item -> item.name()).toList());
                var result = protectedClient.callTool(new CallToolRequest("simulation.set-state",
                        Map.of("targetId", SimulationTestFixture.TARGET_ID, "desiredState", "PAUSED"),
                        Map.of()));
                assertTrue(result.isError());
                var forged = Map.<String, Object>of("actionId", "200", "incidentId", "100",
                        "executionId", "300", "executionKey", "key", "requestId", "dispatch-300",
                        "ticket", "a".repeat(64), "bindingVersion", "action-binding-v1");
                for (int attempt = 0; attempt < 2; attempt++) {
                    var denied = protectedClient.callTool(new CallToolRequest("simulation.set-state",
                            Map.of("targetId", SimulationTestFixture.TARGET_ID,
                                    "desiredState", attempt == 0 ? "PAUSED" : "READY"), forged));
                    assertTrue(denied.isError());
                }
                assertEquals(SimulationTestFixture.INITIAL_STATE, fixture.state());
                assertEquals(0, fixture.applicationCount());
            }
            var reconcileUrl = URI.create(base + "/mcp/incident/reconcile");
            var body = "{\"executionId\":\"300\",\"executionKey\":\"missing\",\"targetId\":\""
                    + SimulationTestFixture.TARGET_ID + "\"}";
            var unauthenticated = HttpClient.newHttpClient().send(HttpRequest.newBuilder(reconcileUrl)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, unauthenticated.statusCode());
            var authenticated = HttpClient.newHttpClient().send(HttpRequest.newBuilder(reconcileUrl)
                    .header("Authorization", "Bearer isolated-test-outbound-token-12345")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, authenticated.statusCode());
            assertTrue(authenticated.body().contains("INDETERMINATE"));
            assertEquals(0, fixture.applicationCount());
        }
    }

    private static void denyDirect(io.modelcontextprotocol.client.McpSyncClient client, String name) {
        try {
            var result = client.callTool(new CallToolRequest(name, Map.of()));
            assertTrue(result.isError(), "unexpected ordinary write: " + name);
        } catch (RuntimeException denied) {
            // Unknown tool is a protocol error on some SDK transports.
        }
    }

    @Configuration
    static class TestIdentity {
        @Bean
        @Primary
        ProtectedServiceAuthentication protectedServiceAuthenticationForTest() {
            return new ProtectedServiceAuthentication(Map.of(
                    "INCIDENT_MCP_OUTBOUND_TOKEN", "isolated-test-outbound-token-12345"));
        }
    }

    private static McpServerFeatures.SyncToolSpecification spec(String name, boolean readOnly) {
        var tool = Tool.builder().name(name).description("test")
                .inputSchema(new JsonSchema("object", Map.of(), List.of(), false, null, null))
                .annotations(new ToolAnnotations(null, readOnly, null, null, null, null)).build();
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> null);
    }
}
