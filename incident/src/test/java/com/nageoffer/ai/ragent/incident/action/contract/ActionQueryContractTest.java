package com.nageoffer.ai.ragent.incident.action.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionQueryService;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionProposalService;
import com.nageoffer.ai.ragent.incident.action.web.ActionController;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActionQueryContractTest {
    @Test
    void httpRoutesEnforceRoleAndKeepIncidentListScoped() throws Exception {
        var query = mock(ActionQueryService.class);
        when(query.get("200")).thenReturn(java.util.Map.of("actionId", "200", "incidentId", "100"));
        when(query.list("100")).thenReturn(List.of(java.util.Map.of("actionId", "200", "incidentId", "100")));
        when(query.list("101")).thenThrow(new ActionDecisionService.NotFoundException());
        var controller = new ActionController(mock(ActionProposalService.class),
                mock(ActionDecisionService.class), mock(ApprovalMapper.class));
        controller.setQueryService(query);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            mvc.perform(get("/incident/v1/actions/200"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.actionId").value("200"));
            mvc.perform(get("/incident/v1/incidents/100/actions"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].incidentId").value("100"));
            mvc.perform(get("/incident/v1/incidents/101/actions"))
                    .andExpect(status().isNotFound());
            stp.when(() -> StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            mvc.perform(get("/incident/v1/actions/200")).andExpect(status().isForbidden());
            mvc.perform(get("/incident/v1/incidents/100/actions")).andExpect(status().isForbidden());
        }
    }

    @Test
    void authorizedQueriesExposeSafeFactsAndFiveOutcomeGuidance() {
        var actions = mock(ActionMapper.class);
        var approvals = mock(ApprovalMapper.class);
        var executions = mock(ExecutionMapper.class);
        var tickets = mock(TicketMapper.class);
        var receipts = mock(ReceiptMapper.class);
        var incidents = mock(IncidentMapper.class);
        var timeline = mock(TimelineMapper.class);
        var incident = new IncidentMapper.IncidentRow();
        incident.setId("100");
        when(incidents.findById("100")).thenReturn(incident);
        var action = new ActionMapper.ActionRow();
        action.setId("200"); action.setIncidentId("100"); action.setState("EXECUTING");
        action.setToolName("simulation.set-state"); action.setTargetId("demo");
        action.setParametersJson("{\"unsafe\":\"secret-value\"}");
        when(actions.findById("200")).thenReturn(action);
        when(actions.findByIncidentId("100")).thenReturn(List.of(action));
        var approval = new ApprovalMapper.ApprovalRow();
        approval.setStatus("APPROVED"); approval.setApprovalDueAt(OffsetDateTime.now());
        when(approvals.findByActionId("200")).thenReturn(approval);
        var execution = new ExecutionMapper.ExecutionRow();
        execution.setId("300"); execution.setExecutionKey("execution-key");
        when(executions.findByActionId("200")).thenReturn(execution);
        var ticket = new TicketMapper.TicketRow();
        ticket.setId("400"); ticket.setStatus("CONSUMED");
        ticket.setSecretDigest("secret-digest"); ticket.setIssuedAt(OffsetDateTime.now());
        ticket.setExpiresAt(OffsetDateTime.now());
        when(tickets.findByExecutionId("300")).thenReturn(List.of(ticket));
        when(receipts.findByExecutionId("300")).thenReturn(List.of());
        when(timeline.findActionFacts("100", "200", "300")).thenReturn(List.of());
        when(timeline.findActionFacts("100", null, null)).thenReturn(List.of());
        var service = new ActionQueryService(actions, approvals, executions, tickets,
                receipts, incidents, timeline, mock(org.springframework.transaction.PlatformTransactionManager.class));
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            var view = service.get("200");
            assertEquals("AWAIT_RECEIPT", view.get("nextStep"));
            assertEquals(1, service.list("100").size());
            assertFalse(view.toString().contains("secret-value"));
            assertFalse(view.toString().contains("secret-digest"));
            assertEquals(false, view.get("parametersAvailable"));
            for (var desired : List.of("READY", "PAUSED")) {
                action.setParametersJson("{\"desiredState\":\""+desired+"\"}");
                var frozen=service.get("200");
                assertEquals(true,frozen.get("parametersAvailable"));
                assertEquals(java.util.Map.of("desiredState",desired),frozen.get("parameters"));
            }
            for(var bad:List.of("{broken", "{\"desiredState\":\"READY\",\"token\":\"secret-value\"}",
                    "{\"desiredState\":\"READY\",\"desiredState\":\"PAUSED\"}")) {
                action.setParametersJson(bad);
                var frozen=service.get("200");assertEquals(false,frozen.get("parametersAvailable"));
                assertFalse(frozen.containsKey("parameters"));assertFalse(frozen.toString().contains("secret-value"));
            }
            var receipt=new ReceiptMapper.ReceiptRow();receipt.setId("500");receipt.setResultStatus("UNKNOWN");
            receipt.setResultCode("TIMEOUT_UNPROVEN");receipt.setDisposition("APPLIED");
            receipt.setOccurredAt(OffsetDateTime.parse("2026-10-04T00:00:00Z"));
            receipt.setSummaryJson("{\"desiredState\":\"READY\",\"applied\":null}");
            when(receipts.findByExecutionId("300")).thenReturn(List.of(receipt));
            execution.setCurrentReceiptId("500");
            var executionView=(java.util.Map<?,?>)service.get("200").get("execution");
            assertEquals("UNKNOWN",executionView.get("resultStatus"));
            Mockito.verify(receipts,Mockito.never()).findById(Mockito.anyString());
            var safeReceipt=(java.util.Map<?,?>)((List<?>)service.get("200").get("receipts")).get(0);
            assertEquals(true,safeReceipt.get("summaryAvailable"));assertEquals("APPLIED",safeReceipt.get("disposition"));
            assertEquals(receipt.getOccurredAt().toString(),safeReceipt.get("occurredAt"));
            assertEquals(null,((java.util.Map<?,?>)safeReceipt.get("summary")).get("applied"));
            for(var bad:List.of("{broken", "{\"desiredState\":\"READY\",\"applied\":true}",
                    "{\"desiredState\":\"READY\",\"applied\":null,\"proofCode\":\"secret-value\"}",
                    "{\"desiredState\":\"READY\",\"applied\":null,\"token\":\"secret-value\"}")) {
                receipt.setSummaryJson(bad);
                var safe=(java.util.Map<?,?>)((List<?>)service.get("200").get("receipts")).get(0);
                assertEquals(false,safe.get("summaryAvailable"));assertFalse(safe.containsKey("summary"));
                assertFalse(safe.toString().contains("secret-value"));
            }
            assertThrows(RuntimeException.class, () -> service.get("201"));
            for (var state : List.of("APPROVAL_REJECTED", "EXPIRED", "FAILED",
                    "EXECUTION_REJECTED", "UNKNOWN")) {
                action.setState(state);
                action.setLastReasonCode("SAFE_REASON");
                var outcome = service.get("200");
                assertEquals(state, outcome.get("state"));
                assertEquals("SAFE_REASON", outcome.get("lastReasonCode"));
                assertEquals("UNKNOWN".equals(state) ? "RECONCILE"
                        : ("FAILED".equals(state) || "EXECUTION_REJECTED".equals(state)
                                ? "REVIEW_EVIDENCE" : "NONE"), outcome.get("nextStep"));
            }
            stp.when(() -> StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            Mockito.clearInvocations(actions, approvals, executions, tickets, receipts, incidents, timeline);
            assertThrows(NotRoleException.class, () -> service.get("200"));
            Mockito.verifyNoInteractions(actions, approvals, executions, tickets, receipts, incidents, timeline);
        }
    }
}
