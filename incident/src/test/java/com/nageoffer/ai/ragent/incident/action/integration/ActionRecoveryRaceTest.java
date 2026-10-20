package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchRecoveryWorker;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionRecoveryRaceTest {
    @Test
    void consumptionWinsWhileRecoveryIsActuallyWaitingOnDatabaseLock() throws Exception {
        raceConsumptionAndRecovery(true);
    }

    @Test
    void recoveryWinsWhileOldConsumptionIsActuallyWaitingOnDatabaseLock() throws Exception {
        raceConsumptionAndRecovery(false);
    }

    private static void raceConsumptionAndRecovery(boolean consumptionWins) throws Exception {
        try (var db = new ActionDbFixture(); var login = Mockito.mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var original = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            var recovery = new ActionDispatchRecoveryWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties);
            var auth = Mockito.mock(ActionServiceAuthentication.class);
            Mockito.when(auth.isAuthenticated("Bearer valid")).thenReturn(true);
            var consumption = new ActionTicketConsumptionService(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, auth);
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var winner = pool.submit(() -> db.guard.inIncident("100", incident -> {
                    locked.countDown();
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS), "test lock was not released");
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    return consumptionWins ? consumption.consume(request(original), "Bearer valid")
                            : recovery.recover("200");
                }));
                assertTrue(locked.await(5, TimeUnit.SECONDS));
                var loser = pool.submit(() -> {
                    if (consumptionWins) return recovery.recover("200");
                    assertThrows(ActionTicketConsumptionService.DeniedException.class,
                            () -> consumption.consume(request(original), "Bearer valid"));
                    return null;
                });
                // Observe a real blocked PostgreSQL session, rather than relying on thread timing.
                long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean blocked = false;
                while (!blocked && System.nanoTime() < waitUntil) {
                    try (var connection = db.openConnection();
                            var statement = connection.createStatement();
                            var result = statement.executeQuery("SELECT count(*) FROM pg_stat_activity "
                                    + "WHERE datname=current_database() AND wait_event_type='Lock'")) {
                        result.next();
                        blocked = result.getInt(1) > 0;
                    }
                    if (!blocked) Thread.sleep(10);
                }
                assertTrue(blocked, "competing operation never waited on a database lock");
                assertTrue(!loser.isDone());
                release.countDown();
                var first = winner.get(10, TimeUnit.SECONDS);
                assertNull(loser.get(10, TimeUnit.SECONDS));
                if (consumptionWins) {
                    assertEquals("GRANTED", ((ActionTicketConsumptionService.ConsumeResult) first).decision());
                    assertEquals(1, db.tickets.findByExecutionId(original.executionId()).size());
                } else {
                    var replacement = (ActionDispatchWorker.DispatchClaim) first;
                    assertEquals(original.executionId(), replacement.executionId());
                    assertEquals(original.executionKey(), replacement.executionKey());
                    assertEquals(original.requestId(), replacement.requestId());
                    var tickets = db.tickets.findByExecutionId(original.executionId());
                    assertEquals(2, tickets.size());
                    assertEquals(1, tickets.stream().filter(t -> "ISSUED".equals(t.getStatus())).count());
                    assertEquals("DISPATCHING", db.actions.findById("200").getState());
                    assertEquals("GRANTED", consumption.consume(request(replacement), "Bearer valid").decision());
                }
                assertEquals("EXECUTING", db.actions.findById("200").getState());
                assertEquals(original.executionId(), db.executions.findByActionId("200").getId());
                assertEquals(1, db.tickets.countConsumed(original.executionId()));
                assertEquals(0, db.tickets.findByExecutionId(original.executionId()).stream()
                        .filter(t -> "ISSUED".equals(t.getStatus())).count());
                assertNull(recovery.recover("200"));
            } finally {
                release.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void lostPlaintextReplacesOnlyUnconsumedTicketOnSameExecution() throws Exception {
        try (var db = new ActionDbFixture(); var login = Mockito.mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var original = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            var recovery = new ActionDispatchRecoveryWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties);
            var replacement = recovery.recover("200");
            assertNotEquals(original.ticket(), replacement.ticket());
            assertEquals(original.executionId(), replacement.executionId());
            assertEquals(original.executionKey(), replacement.executionKey());
            assertEquals(2, db.tickets.findByExecutionId(original.executionId()).size());
            assertEquals("EXPIRED", db.tickets.findByExecutionId(original.executionId()).get(0).getStatus());
            assertEquals("ISSUED", db.tickets.findByExecutionId(original.executionId()).get(1).getStatus());
            var auth = Mockito.mock(ActionServiceAuthentication.class);
            Mockito.when(auth.isAuthenticated("Bearer valid")).thenReturn(true);
            var consume = new ActionTicketConsumptionService(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, auth);
            assertThrows(ActionTicketConsumptionService.DeniedException.class,
                    () -> consume.consume(request(original), "Bearer valid"));
            assertEquals("DISPATCHING", db.actions.findById("200").getState());
            assertEquals("GRANTED", consume.consume(request(replacement), "Bearer valid").decision());
            assertNull(recovery.recover("200"));
            assertEquals(1, db.tickets.countConsumed(original.executionId()));
        }
    }

    @Test
    void authorizationDeadlinePreventsReplacement() throws Exception {
        try (var db = new ActionDbFixture(); var login = Mockito.mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var original = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            db.sql("UPDATE t_incident_action SET authorization_due_at=clock_timestamp()-interval '1 second' "
                    + "WHERE id='200'");
            var recovery = new ActionDispatchRecoveryWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties);
            assertNull(recovery.recover("200"));
            assertEquals(1, db.tickets.findByExecutionId(original.executionId()).size());
            assertEquals("EXPIRED", db.actions.findById("200").getState());
        }
    }

    private static ActionTicketConsumptionService.ConsumeRequest request(
            ActionDispatchWorker.DispatchClaim claim) {
        return new ActionTicketConsumptionService.ConsumeRequest(claim.ticket(), claim.actionId(),
                claim.incidentId(), claim.executionId(), claim.executionKey(), claim.requestId(),
                claim.bindingVersion(), claim.toolName(), claim.toolVersion(), "SIM_SERVICE",
                claim.targetId(), claim.parametersJson());
    }
}
