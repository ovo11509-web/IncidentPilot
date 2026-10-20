package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

class ActionDispatchTicketTest {
    @Test
    void approvedActionCreatesOneExecutionAndOneShortLivedOpaqueTicket() throws Exception {
        try (var db = new ActionDbFixture(); var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var worker = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties);
            var issued = worker.claim("200");
            assertNotNull(issued);
            assertTrue(Pattern.matches("[0-9a-f]{64}", issued.ticket()));
            assertEquals("DISPATCHING", db.actions.findById("200").getState());
            var execution = db.executions.findByActionId("200");
            assertEquals(issued.executionId(), execution.getId());
            assertEquals(db.actions.findById("200").getExecutionKey(), execution.getExecutionKey());
            var persistedTicket = db.tickets.findByExecutionId(execution.getId()).get(0);
            assertEquals("ISSUED", persistedTicket.getStatus());
            assertTrue(persistedTicket.getExpiresAt().isBefore(db.actions.findById("200")
                    .getAuthorizationDueAt().plusNanos(1)));
            assertThrows(ActionDispatchWorker.ConflictException.class, () -> worker.claim("200"));
            assertEquals(1, db.tickets.findByExecutionId(execution.getId()).size());
        }
    }

    @Test
    void ticketConsumptionChecksServiceIdentityBindingAndOneTimeState() throws Exception {
        try (var db = new ActionDbFixture(); var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var claim = new ActionDispatchWorker(db.actions, db.approvals, db.executions, db.tickets,
                    db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            var auth = Mockito.mock(ActionServiceAuthentication.class);
            when(auth.isAuthenticated("Bearer valid")).thenReturn(true);
            var service = new ActionTicketConsumptionService(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, auth);
            var request = new ActionTicketConsumptionService.ConsumeRequest(claim.ticket(),
                    claim.actionId(), claim.incidentId(), claim.executionId(), claim.executionKey(),
                    claim.requestId(), claim.bindingVersion(), claim.toolName(), claim.toolVersion(),
                    "SIM_SERVICE", claim.targetId(), claim.parametersJson());
            assertThrows(ActionTicketConsumptionService.UnauthorizedException.class,
                    () -> service.consume(request, "Bearer wrong"));
            assertThrows(ActionTicketConsumptionService.DeniedException.class,
                    () -> service.consume(new ActionTicketConsumptionService.ConsumeRequest(claim.ticket(),
                            claim.actionId(), claim.incidentId(), claim.executionId(), claim.executionKey(),
                            claim.requestId(), claim.bindingVersion(), claim.toolName(), claim.toolVersion(),
                            "SIM_SERVICE", claim.targetId(), "{\"desiredState\":\"READY\"}"), "Bearer valid"));
            assertEquals("DISPATCHING", db.actions.findById("200").getState());
            assertEquals("GRANTED", service.consume(request, "Bearer valid").decision());
            assertEquals("EXECUTING", db.actions.findById("200").getState());
            assertNotNull(db.executions.findByActionId("200").getReceiptDueAt());
            assertThrows(ActionTicketConsumptionService.DeniedException.class,
                    () -> service.consume(request, "Bearer valid"));
            assertEquals("CONSUMED", db.tickets.findByExecutionId(claim.executionId()).get(0).getStatus());
        }
    }
}
