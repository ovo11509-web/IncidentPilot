package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReconciliationService;
import com.nageoffer.ai.ragent.incident.action.service.ActionManualConclusionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker;
import com.nageoffer.ai.ragent.incident.action.worker.ActionReceiptDeadlineWorker;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionUnknownConvergenceTest {
    @Test
    void indeterminateEvidenceStaysUnknownButStoppedRollbackCanConvergeFailed() throws Exception {
        try (var db = new ActionDbFixture(); var login = Mockito.mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var claim = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            db.sql("UPDATE t_incident_action_execution SET execution_started_at=clock_timestamp(), "
                    + "receipt_due_at=clock_timestamp() WHERE id='" + claim.executionId() + "'");
            db.sql("UPDATE t_incident_action SET state='UNKNOWN' WHERE id='200'");
            var uncertain = new ActionReconciliationService.Evidence("INDETERMINATE",
                    claim.executionId(), claim.executionKey(), claim.targetId(),
                    "READY", null, null, null);
            var noProof = new ActionReconciliationService(db.actions, db.executions,
                    db.guard, db.actionTimeline, db.alerts, (id, key, target) -> uncertain);
            assertEquals("UNKNOWN", noProof.reconcile("200"));
            var rollback = new ActionReconciliationService.Evidence("PROVEN_NOT_APPLIED",
                    claim.executionId(), claim.executionKey(), claim.targetId(),
                    "READY", null, null, "simulation-rollback:" + claim.executionKey());
            var proven = new ActionReconciliationService(db.actions, db.executions,
                    db.guard, db.actionTimeline, db.alerts, (id, key, target) -> rollback);
            assertEquals("FAILED", proven.reconcile("200"));
            assertEquals("FAILED", db.actions.findById("200").getState());
            db.saveConvergenceEvidence("B10-indeterminate-then-rollback", claim.ticket());
        }
    }

    @Test
    void manualEvidenceRequiresUnknownVersionAndStableRequestIdentity() throws Exception {
        try (var db = new ActionDbFixture(); var login = Mockito.mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var claim = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            db.sql("UPDATE t_incident_action_execution SET execution_started_at=clock_timestamp(), "
                    + "receipt_due_at=clock_timestamp() WHERE id='" + claim.executionId() + "'");
            db.sql("UPDATE t_incident_action SET state='UNKNOWN' WHERE id='200'");
            var manual = new ActionManualConclusionService(db.actions, db.executions, db.guard,
                    db.actionTimeline, db.timeline, db.alerts);
            long version = db.actions.findById("200").getVersion();
            var denied = new java.util.concurrent.atomic.AtomicBoolean(true);
            login.when(() -> StpUtil.checkRole("admin")).thenAnswer(invocation -> {
                if (denied.get()) throw new IllegalStateException("permission denied");
                return null;
            });
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> manual.conclude("200", version, "FAILED",
                            "人工核对模拟执行记录", "case:INC-1", "request-1"));
            assertEquals("UNKNOWN", db.actions.findById("200").getState());
            denied.set(false);
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> manual.conclude("200", version, "FAILED", "", "case:INC-1", "request-1"));
            assertEquals("FAILED", manual.conclude("200", version, "FAILED",
                    "人工核对模拟执行记录", "case:INC-1", "request-1"));
            assertEquals("FAILED", manual.conclude("200", version, "FAILED",
                    "人工核对模拟执行记录", "case:INC-1", "request-1"));
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> manual.conclude("200", version, "SUCCEEDED",
                            "冲突结论", "case:INC-2", "request-1"));
            org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                    () -> manual.conclude("200", version, "SUCCEEDED",
                            "冲突结论", "case:INC-2", "request-2"));
            assertEquals("FAILED", db.actions.findById("200").getState());
            db.saveConvergenceEvidence("B10-manual-permission-replay-conflict", claim.ticket());
        }
    }

    @Test
    void readOnlyAppliedEvidenceConvergesUnknownAndConflictDoesNotOverwrite() throws Exception {
        try (var db = new ActionDbFixture(); var login = Mockito.mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var claim = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            var auth = Mockito.mock(ActionServiceAuthentication.class);
            Mockito.when(auth.isAuthenticated("Bearer valid")).thenReturn(true);
            new ActionTicketConsumptionService(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, auth).consume(
                    new ActionTicketConsumptionService.ConsumeRequest(claim.ticket(), claim.actionId(),
                            claim.incidentId(), claim.executionId(), claim.executionKey(), claim.requestId(),
                            claim.bindingVersion(), claim.toolName(), claim.toolVersion(), "SIM_SERVICE",
                            claim.targetId(), claim.parametersJson()), "Bearer valid");
            db.sql("UPDATE t_incident_action SET state='UNKNOWN' WHERE id='200'");
            var applied = new ActionReconciliationService.Evidence("APPLIED", claim.executionId(),
                    claim.executionKey(), claim.targetId(), "READY", "READY", "PAUSED",
                    "simulation-application:" + claim.executionKey());
            var service = new ActionReconciliationService(db.actions, db.executions,
                    db.guard, db.actionTimeline, db.alerts, (id, key, target) -> applied);
            assertEquals("SUCCEEDED", service.reconcile("200"));
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            var contrary = new ActionReconciliationService.Evidence("PROVEN_NOT_APPLIED",
                    claim.executionId(), claim.executionKey(), claim.targetId(), "READY",
                    null, null, "simulation-rollback:" + claim.executionKey());
            var conflicting = new ActionReconciliationService(db.actions, db.executions,
                    db.guard, db.actionTimeline, db.alerts, (id, key, target) -> contrary);
            assertEquals("SUCCEEDED", conflicting.reconcile("200"));
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            db.saveConvergenceEvidence("B10-applied-then-contrary", claim.ticket());
        }
    }

    @Test
    void missedAbsoluteReceiptDeadlineBecomesUnknownAndLateBoundReceiptConverges() throws Exception {
        try (var db = new ActionDbFixture(); var login = Mockito.mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var claim = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            var auth = Mockito.mock(ActionServiceAuthentication.class);
            Mockito.when(auth.isAuthenticated("Bearer valid")).thenReturn(true);
            var consumption = new ActionTicketConsumptionService(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, auth);
            consumption.consume(new ActionTicketConsumptionService.ConsumeRequest(claim.ticket(),
                    claim.actionId(), claim.incidentId(), claim.executionId(), claim.executionKey(),
                    claim.requestId(), claim.bindingVersion(), claim.toolName(), claim.toolVersion(),
                    "SIM_SERVICE", claim.targetId(), claim.parametersJson()), "Bearer valid");
            db.sql("UPDATE t_incident_action_execution SET receipt_due_at=clock_timestamp()-interval '1 second' "
                    + "WHERE id='" + claim.executionId() + "'");
            var deadline = new ActionReceiptDeadlineWorker(db.actions, db.executions, db.receipts,
                    db.guard, db.actionTimeline, db.alerts);
            var reconciliation = Mockito.mock(ActionReceiptDeadlineWorker.ReconciliationPort.class);
            deadline.setReconciliationPort(reconciliation);
            assertEquals(1, deadline.sweepDue());
            Mockito.verify(reconciliation).requestReadOnly(claim.executionId());
            assertEquals("UNKNOWN", db.actions.findById("200").getState());
            assertEquals(0, deadline.sweepDue());
            var receipts = new ActionReceiptService(db.actions, db.executions, db.receipts,
                    db.guard, db.actionTimeline, db.actionOutbox, db.alerts, auth);
            var result = receipts.accept(new ActionReceiptService.Submission(1, "990", claim.actionId(),
                    claim.incidentId(), claim.executionId(), claim.executionKey(), claim.requestId(),
                    claim.toolName(), claim.toolVersion(), "SIM_SERVICE", claim.targetId(), "SUCCESS",
                    "APPLIED", OffsetDateTime.now(), "{\"desiredState\":\"PAUSED\",\"applied\":true}"),
                    "Bearer valid");
            assertEquals("APPLIED", result.disposition());
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            db.saveConvergenceEvidence("B09-fixture-deadline-late-receipt", claim.ticket());
        }
    }
}
