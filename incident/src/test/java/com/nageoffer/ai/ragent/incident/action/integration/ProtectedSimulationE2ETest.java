package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionClosureInvalidator;
import com.nageoffer.ai.ragent.incident.action.service.ActionReconciliationService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.web.ActionInternalController;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker;
import com.nageoffer.ai.ragent.incident.action.worker.ActionReceiptDeadlineWorker;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import com.nageoffer.ai.ragent.incident.core.service.IncidentStateMachine;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Main Incident services and a separately started MCP JVM, both bound only to disposable databases. */
class ProtectedSimulationE2ETest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String OUTBOUND = "isolated-outbound-token-123456789012345";
    private static final String CALLBACK = "isolated-callback-token-123456789012345";

    @Test
    void approvedActionAppliesExactlyOnceAcrossProcesses() throws Exception {
        var secrets = new java.util.ArrayList<>(java.util.List.of(OUTBOUND, CALLBACK));
        Path proof = Path.of("target", "b-catalogue-mcp", java.util.UUID.randomUUID().toString());
        Files.createDirectories(proof);
        try (var db = new ActionDbFixture();
                var bit = new PostgreSQLContainer("postgres:16-alpine");
                var login = Mockito.mockStatic(StpUtil.class)) {
            bit.start();
            try (var connection = DriverManager.getConnection(bit.getJdbcUrl(), bit.getUsername(), bit.getPassword());
                    var statement = connection.createStatement()) {
                try (var source = getClass().getClassLoader().getResourceAsStream("db/bit-schema.sql")) {
                    if (source == null) throw new IllegalStateException("isolated simulation schema missing");
                    statement.execute(new String(source.readAllBytes(), StandardCharsets.UTF_8));
                }
                statement.executeUpdate("INSERT INTO simulation_target(target_id,state) VALUES('demo','READY')");
            }
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var claim = new ActionDispatchWorker(db.actions, db.approvals, db.executions, db.tickets,
                    db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            secrets.add(claim.ticket());
            var auth = mock(ActionServiceAuthentication.class);
            when(auth.isAuthenticated("Bearer " + CALLBACK)).thenReturn(true);
            var consumption = new ActionTicketConsumptionService(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, auth);
            var receipts = new ActionReceiptService(db.actions, db.executions, db.receipts, db.guard,
                    db.actionTimeline, db.actionOutbox, db.alerts, auth);
            var controller = new ActionInternalController(consumption, receipts);
            var rejectReceipts = new AtomicBoolean();
            var blockNextConsume = new AtomicBoolean();
            var consumedBeforeCrash = new CountDownLatch(1);
            var releaseBlockedConsume = new CountDownLatch(1);
            var callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            callbackServer.createContext("/incident/internal/v1/action-tickets/consume", exchange ->
                    respond(exchange, body -> {
                        var decision = controller.consume(body, exchange.getRequestHeaders()
                                .getFirst("Authorization"), null).getBody();
                        if (blockNextConsume.getAndSet(false)) {
                            consumedBeforeCrash.countDown();
                            try {
                                releaseBlockedConsume.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        return decision;
                    }));
            callbackServer.createContext("/incident/internal/v1/action-receipts", exchange -> {
                if (rejectReceipts.get()) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                } else {
                    respond(exchange, body -> controller.receipt(body, exchange.getRequestHeaders()
                            .getFirst("Authorization")).getBody());
                }
            });
            callbackServer.start();
            int mcpPort = availablePort();
            Path output = proof.resolve("mcp-process.log").toAbsolutePath();
            Files.createDirectories(output.getParent());
            Process child = null;
            try {
                String classpath = System.getProperty("surefire.test.class.path");
                if (classpath == null || classpath.isBlank()) {
                    throw new IllegalStateException("Surefire test classpath unavailable");
                }
                var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe")
                        .toString(), "-cp", classpath,
                        "com.nageoffer.ai.ragent.mcp.McpServerApplication",
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
                awaitServer(child, base);
                assertEquals(401, HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                                URI.create(base + "/mcp/incident")).GET().build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode());
                try (var ordinary = McpClient.sync(HttpClientStreamableHttpTransport.builder(base)
                        .build()).build()) {
                    ordinary.initialize();
                    assertFalse(ordinary.listTools().tools().stream()
                            .anyMatch(tool -> tool.name().equals("set_cart_item")
                                    || tool.name().equals("simulation.set-state")));
                    assertThrows(RuntimeException.class,
                            () -> ordinary.callTool(new CallToolRequest("set_cart_item", Map.of())));
                    assertThrows(RuntimeException.class,
                            () -> ordinary.callTool(new CallToolRequest("simulation.set-state", Map.of())));
                    assertEquals(0, applicationCount(bit));
                }
                saveSnapshot(proof, "B04-ordinary-and-unauthenticated", db, bit, secrets);
                try (var mcp = McpClient.sync(HttpClientStreamableHttpTransport.builder(base)
                        .endpoint("/mcp/incident")
                        .customizeRequest(request -> request.header("Authorization", "Bearer " + OUTBOUND))
                        .build()).build()) {
                    mcp.initialize();
                    var args = Map.<String, Object>of("targetId", "demo", "desiredState", "PAUSED");
                    var meta = Map.<String, Object>of("actionId", claim.actionId(),
                            "incidentId", claim.incidentId(), "executionId", claim.executionId(),
                            "executionKey", claim.executionKey(), "requestId", claim.requestId(),
                            "ticket", claim.ticket(), "bindingVersion", claim.bindingVersion());
                    db.seedPendingAction("210", OffsetDateTime.now().plusMinutes(10));
                    new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                            db.actionOutbox, db.properties, db.alerts).decide("210", 0, "REJECT", "unsafe", "99");
                    assertEquals("APPROVAL_REJECTED", db.actions.findById("210").getState());
                    assertThrows(ActionDispatchWorker.ConflictException.class,
                            () -> new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("210"));
                    db.seedPendingAction("211", OffsetDateTime.now().minusSeconds(1));
                    assertThrows(ActionDecisionService.ConflictException.class,
                            () -> new ActionDecisionService(db.actions, db.approvals, db.guard,
                                    db.actionTimeline, db.actionOutbox, db.properties, db.alerts)
                                    .decide("211", 0, "APPROVE", null, "99"));
                    assertEquals("EXPIRED", db.actions.findById("211").getState());
                    db.seedPendingAction("212", OffsetDateTime.now().plusMinutes(10));
                    new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                            db.actionOutbox, db.properties, db.alerts).decide("212", 0, "APPROVE", null, "99");
                    db.sql("UPDATE t_incident_action SET authorization_due_at=clock_timestamp()-interval '1 second' "
                            + "WHERE id='212'");
                    db.sql("UPDATE t_incident_action_approval SET authorization_due_at=clock_timestamp()-interval '1 second' "
                            + "WHERE action_id='212'");
                    assertThrows(ActionDispatchWorker.ConflictException.class,
                            () -> new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("212"));
                    db.seedPendingAction("213", OffsetDateTime.now().plusMinutes(10));
                    new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                            db.actionOutbox, db.properties, db.alerts).decide("213", 0, "APPROVE", null, "99");
                    var expiredTicket = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                            db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("213");
                    secrets.add(expiredTicket.ticket());
                    db.sql("UPDATE t_incident_execution_ticket SET issued_at=clock_timestamp()-interval '2 minutes', "
                            + "expires_at=clock_timestamp()-interval '1 second' WHERE execution_id='"
                            + expiredTicket.executionId() + "'");
                    assertTrue(mcp.callTool(new CallToolRequest("simulation.set-state",
                            args, metadata(expiredTicket))).isError());
                    assertEquals("DISPATCHING", db.actions.findById("213").getState());
                    assertEquals(0, applicationCount(bit));
                    var tampered = mcp.callTool(new CallToolRequest("simulation.set-state",
                            Map.of("targetId", "demo", "desiredState", "READY"), meta));
                    assertTrue(tampered.isError());
                    assertTrue(mcp.callTool(new CallToolRequest("simulation.set-state",
                            Map.of("targetId", "unregistered", "desiredState", "PAUSED"), meta)).isError());
                    for (String field : java.util.List.of("actionId", "incidentId", "executionId",
                            "executionKey", "requestId", "ticket", "bindingVersion")) {
                        var changed = new java.util.HashMap<String, Object>(meta);
                        changed.put(field, "tampered-value");
                        assertTrue(mcp.callTool(new CallToolRequest("simulation.set-state",
                                args, changed)).isError(), field);
                    }
                    assertEquals("DISPATCHING", db.actions.findById("200").getState());
                    assertEquals("READY", targetState(bit));
                    assertEquals(0, applicationCount(bit));
                    saveSnapshot(proof, "B02-B05-pre-effect-refusals", db, bit, secrets);
                    var first = mcp.callTool(new CallToolRequest("simulation.set-state", args, meta));
                    assertFalse(first.isError());
                    assertEquals("SUCCEEDED", db.actions.findById("200").getState());
                    assertEquals(1, db.receipts.findByExecutionId(claim.executionId()).size());
                    assertEquals("PAUSED", targetState(bit));
                    assertEquals(1, applicationCount(bit));
                    // The response path submits the exact object again; it must be idempotent.
                    var duplicate = JSON.valueToTree(first.structuredContent());
                    var reply = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + callbackServer.getAddress().getPort()
                                            + "/incident/internal/v1/action-receipts"))
                            .header("Authorization", "Bearer " + CALLBACK)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(duplicate.toString())).build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, reply.statusCode());
                    assertEquals(1, db.receipts.findByExecutionId(claim.executionId()).size());
                    var replay = mcp.callTool(new CallToolRequest("simulation.set-state", args, meta));
                    assertTrue(replay.isError());
                    assertEquals(1, applicationCount(bit));
                    saveSnapshot(proof, "B01-B05-success-and-replay", db, bit, secrets);

                    // Both receipt paths disappear after a committed simulation application.
                    db.seedPendingAction("201", OffsetDateTime.now().plusMinutes(10));
                    new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                            db.actionOutbox, db.properties, db.alerts).decide("201", 0, "APPROVE", null, "99");
                    var second = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                            db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("201");
                    secrets.add(second.ticket());
                    var secondMeta = metadata(second);
                    rejectReceipts.set(true);
                    var lostReceipt = mcp.callTool(new CallToolRequest("simulation.set-state", args, secondMeta));
                    rejectReceipts.set(false);
                    assertFalse(lostReceipt.isError());
                    assertEquals("EXECUTING", db.actions.findById("201").getState());
                    assertEquals(0, db.receipts.findByExecutionId(second.executionId()).size());
                    assertEquals(2, applicationCount(bit));
                    db.sql("UPDATE t_incident_action_execution SET receipt_due_at=clock_timestamp()-interval '1 second' "
                            + "WHERE id='" + second.executionId() + "'");
                    var unavailable = new ActionReconciliationService(db.actions, db.executions,
                            db.guard, db.actionTimeline, db.alerts,
                            (id, key, target) -> { throw new IllegalStateException("read-only endpoint unavailable"); });
                    var deadline = new ActionReceiptDeadlineWorker(db.actions, db.executions, db.receipts,
                            db.guard, db.actionTimeline, db.alerts);
                    deadline.setReconciliationPort(unavailable);
                    assertEquals(1, deadline.sweepDue());
                    assertEquals("UNKNOWN", db.actions.findById("201").getState());
                    saveSnapshot(proof, "B09-fixture-deadline-unknown", db, bit, secrets);
                    var reader = new ActionReconciliationService(db.actions, db.executions,
                            db.guard, db.actionTimeline, db.alerts, (id, key, target) -> {
                                try {
                                    var evidenceRequest = HttpRequest.newBuilder(URI.create(base
                                                    + "/mcp/incident/reconcile"))
                                            .header("Authorization", "Bearer " + OUTBOUND)
                                            .header("Content-Type", "application/json")
                                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                                                    Map.of("executionId", id, "executionKey", key,
                                                            "targetId", target)))).build();
                                    var evidenceReply = HttpClient.newHttpClient().send(evidenceRequest,
                                            HttpResponse.BodyHandlers.ofString());
                                    assertEquals(200, evidenceReply.statusCode());
                                    return JSON.readValue(evidenceReply.body(),
                                            ActionReconciliationService.Evidence.class);
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
                    assertEquals("SUCCEEDED", reader.reconcile("201"));
                    assertEquals(2, applicationCount(bit));
                    saveSnapshot(proof, "B10-real-read-only-applied", db, bit, secrets);

                    // A database lock makes a short, frozen execution limit expire in the child JVM.
                    db.seedPendingAction("203", OffsetDateTime.now().plusMinutes(10));
                    new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                            db.actionOutbox, db.properties, db.alerts).decide("203", 0, "APPROVE", null, "99");
                    db.properties.setMaxExecutionDuration(java.time.Duration.ofMillis(100));
                    var timed = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                            db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("203");
                    secrets.add(timed.ticket());
                    assertEquals(100, db.executions.findByActionId("203").getMaxExecutionDurationMs());
                    try (var bitLock = DriverManager.getConnection(bit.getJdbcUrl(),
                            bit.getUsername(), bit.getPassword())) {
                        bitLock.setAutoCommit(false);
                        try (var lockStatement = bitLock.createStatement();
                                var lockedTarget = lockStatement.executeQuery(
                                        "SELECT target_id FROM simulation_target WHERE target_id='demo' FOR UPDATE")) {
                            assertTrue(lockedTarget.next());
                        }
                        var timeoutReceipt = mcp.callTool(new CallToolRequest("simulation.set-state",
                                args, metadata(timed)));
                        assertFalse(timeoutReceipt.isError());
                        var resultCode = JSON.valueToTree(timeoutReceipt.structuredContent())
                                .path("resultCode").asText();
                        assertEquals("TIMEOUT_PROVEN_ROLLED_BACK", resultCode);
                        assertEquals("FAILED", db.actions.findById("203").getState());
                        bitLock.rollback();
                    }
                    assertEquals(2, applicationCount(bit));
                    assertEquals("PAUSED", targetState(bit));
                    saveSnapshot(proof, "B08-proven-rollback", db, bit, secrets);

                    // Blocking the evidence table removes rollback proof, so timeout stays UNKNOWN.
                    db.seedPendingAction("204", OffsetDateTime.now().plusMinutes(10));
                    new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                            db.actionOutbox, db.properties, db.alerts).decide("204", 0, "APPROVE", null, "99");
                    var unproven = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                            db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("204");
                    secrets.add(unproven.ticket());
                    try (var evidenceLock = DriverManager.getConnection(bit.getJdbcUrl(),
                            bit.getUsername(), bit.getPassword())) {
                        evidenceLock.setAutoCommit(false);
                        try (var lockStatement = evidenceLock.createStatement()) {
                            lockStatement.execute("LOCK TABLE simulation_application IN ACCESS EXCLUSIVE MODE");
                        }
                        var unknownReceipt = mcp.callTool(new CallToolRequest("simulation.set-state",
                                args, metadata(unproven)));
                        assertFalse(unknownReceipt.isError());
                        assertEquals("UNKNOWN", JSON.valueToTree(unknownReceipt.structuredContent())
                                .path("resultStatus").asText());
                        assertEquals("TIMEOUT_UNPROVEN", JSON.valueToTree(unknownReceipt.structuredContent())
                                .path("resultCode").asText());
                        assertEquals("UNKNOWN", db.actions.findById("204").getState());
                        evidenceLock.rollback();
                    }
                    assertEquals(2, applicationCount(bit));
                    assertEquals("PAUSED", targetState(bit));
                    saveSnapshot(proof, "B08-unproven-unknown", db, bit, secrets);

                    // The callback commits consumption, then the separate MCP JVM dies before tool start.
                    db.seedPendingAction("202", OffsetDateTime.now().plusMinutes(10));
                    new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                            db.actionOutbox, db.properties, db.alerts).decide("202", 0, "APPROVE", null, "99");
                    var third = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                            db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("202");
                    secrets.add(third.ticket());
                    blockNextConsume.set(true);
                    var inFlight = CompletableFuture.supplyAsync(() -> mcp.callTool(new CallToolRequest(
                            "simulation.set-state", args, metadata(third))));
                    assertTrue(consumedBeforeCrash.await(10, TimeUnit.SECONDS));
                    db.sql("UPDATE t_incident SET lifecycle_state='RESOLVED', "
                            + "lifecycle_sealed_at=clock_timestamp(), aggregation_slot_active=false WHERE id='100'");
                    var transitions = new IncidentTransitionService(db.incidents, db.alerts, db.clock,
                            new IncidentStateMachine(), db.incidentTimeline, db.transactions);
                    transitions.setActionClosureInvalidator(new ActionClosureInvalidator(db.actions,
                            db.approvals, db.executions, db.tickets, db.incidents, db.actionTimeline));
                    transitions.transition("100", "RESOLVED", db.incidents.findById("100").getVersion(),
                            "CLOSED", "resolved", "42", "close-after-consumption");
                    assertEquals("EXECUTING", db.actions.findById("202").getState());
                    assertEquals("EXPIRED", db.actions.findById("213").getState());
                    assertEquals("EXPIRED", db.tickets.findByExecutionId(expiredTicket.executionId())
                            .get(0).getStatus());
                    assertEquals(2, applicationCount(bit));
                    child.destroyForcibly();
                    assertTrue(child.waitFor(10, TimeUnit.SECONDS));
                    releaseBlockedConsume.countDown();
                    try {
                        inFlight.get(10, TimeUnit.SECONDS);
                    } catch (java.util.concurrent.ExecutionException expected) {
                        // Transport outcome is uncertain after the separate server dies.
                    }
                    assertEquals("EXECUTING", db.actions.findById("202").getState());
                    assertEquals(2, applicationCount(bit));
                    var late = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(
                                    "http://127.0.0.1:" + callbackServer.getAddress().getPort()
                                            + "/incident/internal/v1/action-receipts"))
                            .header("Authorization", "Bearer " + CALLBACK)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    JSON.valueToTree(lostReceipt.structuredContent()).toString())).build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, late.statusCode());
                    assertEquals("SUCCEEDED", db.actions.findById("201").getState());
                    db.sql("UPDATE t_incident_action_execution SET receipt_due_at=clock_timestamp()-interval '1 second' "
                            + "WHERE id='" + third.executionId() + "'");
                    var crashDeadline = new ActionReceiptDeadlineWorker(db.actions, db.executions,
                            db.receipts, db.guard, db.actionTimeline, db.alerts);
                    crashDeadline.setReconciliationPort(unavailable);
                    assertEquals(1, crashDeadline.sweepDue());
                    assertEquals("UNKNOWN", db.actions.findById("202").getState());
                    assertEquals(2, applicationCount(bit));
                    saveSnapshot(proof, "B07-consumed-mcp-crash", db, bit, secrets);
                }
            } finally {
                if (child != null) {
                    child.destroy();
                    if (!child.waitFor(5, TimeUnit.SECONDS)) child.destroyForcibly();
                }
                callbackServer.stop(0);
                String childLog = Files.readString(output);
                for (String secret : secrets) assertFalse(childLog.contains(secret), "credential in MCP log");
            }
            Files.writeString(proof.resolve("summary.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
                    JSON.createObjectNode().put("passed", true).put("realMcp", true)
                            .put("mainApplication", "Incident services with test HTTP bridge and authentication substitute")
                            .put("deadlines", "fixture SQL; frozen MCP duration 100ms for timeout cases")
                            .put("actualSecretsScanned", secrets.size()).put("secretLeaks", 0)));
        }
    }

    private static void saveSnapshot(Path proof, String scenario, ActionDbFixture db,
            PostgreSQLContainer bit, java.util.List<String> secrets) throws Exception {
        var snapshot = db.evidenceSnapshot().put("scenario", scenario)
                .put("simulationApplications", applicationCount(bit)).put("targetState", targetState(bit));
        String content = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot);
        for (String secret : secrets) assertFalse(content.contains(secret), "credential in MCP evidence");
        Files.writeString(proof.resolve(scenario + ".json"), content);
    }

    private static Map<String, Object> metadata(ActionDispatchWorker.DispatchClaim claim) {
        return Map.of("actionId", claim.actionId(), "incidentId", claim.incidentId(),
                "executionId", claim.executionId(), "executionKey", claim.executionKey(),
                "requestId", claim.requestId(), "ticket", claim.ticket(),
                "bindingVersion", claim.bindingVersion());
    }

    static void respond(com.sun.net.httpserver.HttpExchange exchange,
            java.util.function.Function<String, Object> handler) throws java.io.IOException {
        int status = 200;
        String body;
        try {
            body = JSON.writeValueAsString(handler.apply(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            status = 409;
            body = "{\"code\":\"DENIED\"}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    static int availablePort() throws Exception {
        try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    static void awaitServer(Process child, String base) throws Exception {
        var http = HttpClient.newHttpClient();
        for (int attempt = 0; attempt < 120; attempt++) {
            if (!child.isAlive()) throw new IllegalStateException("isolated MCP process exited");
            try {
                var request = HttpRequest.newBuilder(URI.create(base + "/mcp/incident")).GET().build();
                if (http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 401) return;
            } catch (Exception ignored) {
                // Wait for the isolated process to bind its local port.
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("isolated MCP process did not start");
    }

    static String targetState(PostgreSQLContainer bit) throws Exception {
        try (var db = DriverManager.getConnection(bit.getJdbcUrl(), bit.getUsername(), bit.getPassword());
                var statement = db.createStatement();
                var rows = statement.executeQuery("SELECT state FROM simulation_target WHERE target_id='demo'")) {
            rows.next();
            return rows.getString(1);
        }
    }

    static long applicationCount(PostgreSQLContainer bit) throws Exception {
        try (var db = DriverManager.getConnection(bit.getJdbcUrl(), bit.getUsername(), bit.getPassword());
                var statement = db.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM simulation_application")) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
