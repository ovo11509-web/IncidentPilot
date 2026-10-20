package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.nageoffer.ai.ragent.incident.action.worker.ActionReceiptDeadlineWorker;
import org.junit.jupiter.api.Test;

class ActionReceiptTest {
    @Test
    void receiptWinsWhileDeadlineSweepWaitsOnDatabaseLock() throws Exception {
        raceReceiptAndDeadline(true);
    }

    @Test
    void deadlineWinsWhileLateReceiptWaitsOnDatabaseLock() throws Exception {
        raceReceiptAndDeadline(false);
    }

    private static void raceReceiptAndDeadline(boolean receiptWins) throws Exception {
        try (var db = new ActionDbFixture()) {
            seedExecuting(db);
            db.sql("UPDATE t_incident_action_execution SET receipt_due_at=clock_timestamp()-interval '1 second' WHERE id='300'");
            var auth = mock(ActionServiceAuthentication.class);
            when(auth.isAuthenticated("Bearer callback")).thenReturn(true);
            var service = new ActionReceiptService(db.actions, db.executions, db.receipts, db.guard,
                    db.actionTimeline, db.actionOutbox, db.alerts, auth);
            var receipt = new ActionReceiptService.Submission(1, "400", "200", "100", "300",
                    db.actions.findById("200").getExecutionKey(), "dispatch-300", "simulation.set-state", "1",
                    "SIM_SERVICE", "demo", "SUCCESS", "SIMULATION_APPLIED", OffsetDateTime.now(),
                    "{\"desiredState\":\"PAUSED\",\"applied\":true}");
            var deadline = new ActionReceiptDeadlineWorker(db.actions, db.executions, db.receipts,
                    db.guard, db.actionTimeline, db.alerts);
            var readOnlyRequests = new AtomicInteger();
            deadline.setReconciliationPort(executionId -> {
                assertEquals("300", executionId);
                readOnlyRequests.incrementAndGet();
            });
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var winner = pool.submit(() -> db.guard.inIncident("100", incident -> {
                    locked.countDown();
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    return receiptWins ? service.accept(receipt, "Bearer callback") : deadline.sweepDue();
                }));
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var loser = pool.submit(() -> receiptWins ? deadline.sweepDue() : service.accept(receipt, "Bearer callback"));
                boolean blocked = false;
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!blocked && System.nanoTime() < until) {
                    try (var connection = db.openConnection(); var statement = connection.createStatement();
                            var rows = statement.executeQuery("SELECT count(*) FROM pg_stat_activity "
                                    + "WHERE datname=current_database() AND wait_event_type='Lock'")) {
                        rows.next();
                        blocked = rows.getInt(1) > 0;
                    }
                    if (!blocked) Thread.sleep(10);
                }
                assertTrue(blocked, "competing receipt/sweep never waited on a PostgreSQL lock");
                assertTrue(!loser.isDone());
                release.countDown();
                Object first = winner.get(10, TimeUnit.SECONDS);
                Object second = loser.get(10, TimeUnit.SECONDS);
                assertEquals(receiptWins ? 0 : 1, receiptWins ? second : first);
                assertEquals("APPLIED", ((ActionReceiptService.Acceptance) (receiptWins ? first : second)).disposition());
                assertEquals("SUCCEEDED", db.actions.findById("200").getState());
                assertEquals("400", db.executions.findByActionId("200").getCurrentReceiptId());
                assertEquals(1, db.receipts.findByExecutionId("300").size());
                assertEquals(receiptWins ? 0 : 1, readOnlyRequests.get());
                var facts = db.timeline.findByIncidentId("100");
                assertEquals(receiptWins ? 1 : 2, facts.size());
                assertEquals("ACTION_RECEIPT_ACCEPTED", facts.get(facts.size() - 1).getEventType());
                if (!receiptWins) assertEquals("ACTION_STATUS_CHANGED", facts.get(0).getEventType());
                var event = db.outbox.findById(facts.get(facts.size() - 1).getId());
                assertEquals("incident.action-receipt-accepted", event.getEventType());
                assertEquals(facts.get(facts.size() - 1).getBusinessEventKey(), event.getBusinessEventKey());
                assertEquals("APPLIED", service.accept(receipt, "Bearer callback").disposition());
                assertEquals(0, deadline.sweepDue());
                assertEquals(facts.size(), db.timeline.findByIncidentId("100").size());
                var json = new com.fasterxml.jackson.databind.ObjectMapper();
                var proof = java.nio.file.Path.of("target", "safety-closeout", "receipt-race-"
                        + java.util.UUID.randomUUID());
                java.nio.file.Files.createDirectories(proof);
                var result = json.createObjectNode().put("scenario", receiptWins ? "receipt-first" : "deadline-first")
                        .put("databaseLockObserved", blocked).put("actionId", "200").put("executionId", "300")
                        .put("receiptId", "400").put("finalState", "SUCCEEDED").put("receipts", 1)
                        .put("readOnlyRequests", readOnlyRequests.get()).put("timelineFacts", facts.size())
                        .put("receiptOutboxEvents", 1).put("realToolInvoked", false);
                var timeline = result.putArray("timeline");
                for (var fact : facts) timeline.addObject().put("eventId", fact.getId())
                        .put("sequenceNo", fact.getSequenceNo()).put("eventType", fact.getEventType())
                        .put("businessEventKey", fact.getBusinessEventKey()).set("summary", json.readTree(fact.getSummaryJson()));
                java.nio.file.Files.writeString(proof.resolve("summary.json"),
                        json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
                java.nio.file.Files.writeString(proof.resolve("outbox.json"), event.getPayloadJson());
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void duplicateReceiptDoesNotReplaceTheCommittedResult() throws Exception {
        try (var db = new ActionDbFixture()) {
            seedExecuting(db);
            var action = db.actions.findById("200");

            var authentication = mock(ActionServiceAuthentication.class);
            when(authentication.isAuthenticated("Bearer callback")).thenReturn(true);
            var service = new ActionReceiptService(db.actions, db.executions, db.receipts, db.guard,
                    db.actionTimeline, db.actionOutbox, db.alerts, authentication);
            var occurredAt = OffsetDateTime.now();
            var receipt = new ActionReceiptService.Submission(1, "400", "200", "100", "300", action.getExecutionKey(),
                    "dispatch-300", "simulation.set-state", "1", "SIM_SERVICE", "demo",
                    "SUCCESS", "SIMULATION_APPLIED", occurredAt,
                    "{\"desiredState\":\"PAUSED\",\"applied\":true}");
            var wrongVersion = new ActionReceiptService.Submission(2, "400", "200", "100", "300",
                    action.getExecutionKey(), "dispatch-300", "simulation.set-state", "1",
                    "SIM_SERVICE", "demo", "SUCCESS", "SIMULATION_APPLIED", occurredAt,
                    "{\"desiredState\":\"PAUSED\",\"applied\":true}");
            assertThrows(IllegalArgumentException.class,
                    () -> service.accept(wrongVersion, "Bearer callback"));
            assertThrows(RuntimeException.class, () -> service.accept(receipt, null));
            assertEquals("APPLIED", service.accept(receipt, "Bearer callback").disposition());
            assertEquals("APPLIED", service.accept(receipt, "Bearer callback").disposition());
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            assertEquals(1, db.receipts.findByExecutionId("300").size());
            var sameResultOtherPath = new ActionReceiptService.Submission(1, "401", "200", "100", "300",
                    action.getExecutionKey(), "dispatch-300", "simulation.set-state", "1",
                    "SIM_SERVICE", "demo", "SUCCESS", "SIMULATION_APPLIED", occurredAt,
                    "{\"desiredState\":\"PAUSED\",\"applied\":true}");
            assertEquals("CONSISTENT", service.accept(sameResultOtherPath, "Bearer callback").disposition());
            var conflicting = new ActionReceiptService.Submission(1, "400", "200", "100", "300", action.getExecutionKey(),
                    "dispatch-300", "simulation.set-state", "1", "SIM_SERVICE", "demo",
                    "FAILED", "TOOL_FAILED", occurredAt,
                    "{\"desiredState\":\"PAUSED\",\"applied\":false,\"proofCode\":\"ROLLED_BACK\"}");
            assertThrows(ActionReceiptService.ConflictException.class,
                    () -> service.accept(conflicting, "Bearer callback"));
            var oppositeTerminal = new ActionReceiptService.Submission(1, "402", "200", "100", "300",
                    action.getExecutionKey(), "dispatch-300", "simulation.set-state", "1",
                    "SIM_SERVICE", "demo", "FAILED", "TOOL_FAILED", occurredAt,
                    "{\"desiredState\":\"PAUSED\",\"applied\":false,\"proofCode\":\"ROLLED_BACK\"}");
            assertThrows(ActionReceiptService.ConflictException.class,
                    () -> service.accept(oppositeTerminal, "Bearer callback"));
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            assertEquals(3, db.receipts.findByExecutionId("300").size());
        }
    }

    @Test
    void authenticatedLateReceiptConvergesUnknownWithoutReexecuting() throws Exception {
        try (var db = new ActionDbFixture()) {
            seedExecuting(db);
            db.sql("UPDATE t_incident_action SET state='UNKNOWN', version=2 WHERE id='200'");
            var authentication = mock(ActionServiceAuthentication.class);
            when(authentication.isAuthenticated("Bearer callback")).thenReturn(true);
            var service = new ActionReceiptService(db.actions, db.executions, db.receipts, db.guard,
                    db.actionTimeline, db.actionOutbox, db.alerts, authentication);
            var receipt = new ActionReceiptService.Submission(1, "403", "200", "100", "300",
                    db.actions.findById("200").getExecutionKey(), "dispatch-300", "simulation.set-state", "1",
                    "SIM_SERVICE", "demo", "SUCCESS", "SIMULATION_APPLIED", OffsetDateTime.now(),
                    "{\"desiredState\":\"PAUSED\",\"applied\":true}");
            assertEquals("APPLIED", service.accept(receipt, "Bearer callback").disposition());
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            assertEquals(1, db.receipts.findByExecutionId("300").size());
        }
    }

    @Test
    void preEffectRejectionRequiresProofAndBecomesDistinctTerminal() throws Exception {
        try (var db = new ActionDbFixture()) {
            seedExecuting(db);
            var authentication = mock(ActionServiceAuthentication.class);
            when(authentication.isAuthenticated("Bearer callback")).thenReturn(true);
            var service = new ActionReceiptService(db.actions, db.executions, db.receipts, db.guard,
                    db.actionTimeline, db.actionOutbox, db.alerts, authentication);
            var key = db.actions.findById("200").getExecutionKey();
            var withoutProof = new ActionReceiptService.Submission(1, "404", "200", "100", "300",
                    key, "dispatch-300", "simulation.set-state", "1", "SIM_SERVICE", "demo",
                    "FAILED", "TIMEOUT_PROVEN_ROLLED_BACK", OffsetDateTime.now(),
                    "{\"desiredState\":\"PAUSED\",\"applied\":false}");
            assertThrows(IllegalArgumentException.class,
                    () -> service.accept(withoutProof, "Bearer callback"));
            assertEquals("EXECUTING", db.actions.findById("200").getState());
            var rejected = new ActionReceiptService.Submission(1, "405", "200", "100", "300",
                    key, "dispatch-300", "simulation.set-state", "1", "SIM_SERVICE", "demo",
                    "REJECTED", "POLICY_REJECTED", OffsetDateTime.now(),
                    "{\"desiredState\":\"PAUSED\",\"applied\":false,"
                            + "\"proofCode\":\"PRE_EFFECT_REJECTION\"}");
            assertEquals("APPLIED", service.accept(rejected, "Bearer callback").disposition());
            assertEquals("EXECUTION_REJECTED", db.actions.findById("200").getState());
        }
    }

    private static void seedExecuting(ActionDbFixture db) throws Exception {
        db.seedIncident("100");
        db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
        db.sql("UPDATE t_incident_action SET state='EXECUTING', authorization_due_at=clock_timestamp() "
                + "+ interval '5 minutes', version=1 WHERE id='200'");
        db.sql("UPDATE t_incident_action_approval SET status='APPROVED', decider_id='42', "
                + "role_snapshot='admin', decided_at=clock_timestamp(), authorization_due_at="
                + "clock_timestamp() + interval '5 minutes', version=1 WHERE action_id='200'");
        var action = db.actions.findById("200");
        var execution = new ExecutionMapper.NewExecution();
        execution.setId("300");
        execution.setActionId("200");
        execution.setExecutionKey(action.getExecutionKey());
        execution.setDispatchRequestId("dispatch-300");
        execution.setToolName(action.getToolName());
        execution.setToolVersion(action.getToolVersion());
        execution.setTargetType(action.getTargetType());
        execution.setTargetId(action.getTargetId());
        execution.setBindingDigest(action.getBindingDigest());
        execution.setMaxExecutionDurationMs(120000);
        execution.setReceiptGracePeriodMs(180000);
        db.executions.insert(execution);
        db.executions.markStarted("300", OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(5));
    }
}
