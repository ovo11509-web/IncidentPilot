package com.nageoffer.ai.ragent.incident.action.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import com.nageoffer.ai.ragent.incident.action.web.ActionInternalController;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.user.config.SaTokenConfig;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActionInternalContractTest {
    @Test
    void callbackAuthPrecedesBodyAndResponseNeverEchoesTicket() throws Exception {
        var service = mock(ActionTicketConsumptionService.class);
        var receipts = mock(ActionReceiptService.class);
        when(service.isAuthenticated("Bearer service-token")).thenReturn(true);
        when(service.consume(any(), eq("Bearer service-token")))
                .thenReturn(new ActionTicketConsumptionService.ConsumeResult("GRANTED", OffsetDateTime.now(), 120000));
        var mvc = MockMvcBuilders.standaloneSetup(new ActionInternalController(service, receipts))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        assertTrueCallbackRoute();
        mvc.perform(post("/incident/internal/v1/action-tickets/consume")
                        .contentType("application/json").content("{bad"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/incident/internal/v1/action-tickets/consume")
                        .header("Authorization", "Bearer service-token")
                        .contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest());
        String body = """
                {"ticket":"secret-not-returned","actionId":"200","incidentId":"100",
                 "executionId":"300","executionKey":"key","requestId":"req",
                 "bindingVersion":"action-binding-v1","toolName":"simulation.set-state",
                 "toolVersion":"1","targetId":"demo","parameters":{"desiredState":"PAUSED"}}
                """;
        mvc.perform(post("/incident/internal/v1/action-tickets/consume")
                        .header("Authorization", "Bearer service-token")
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.decision").value("GRANTED"))
                .andExpect(jsonPath("$.data.maxExecutionDurationMs").value(120000))
                .andExpect(jsonPath("$.data.ticket").doesNotExist());
    }

    @Test
    void receiptCallbackAuthenticatesBeforeParsingAndReturnsOnlySafeStatus() throws Exception {
        var service = mock(ActionTicketConsumptionService.class);
        var receipts = mock(ActionReceiptService.class);
        when(service.isAuthenticated("Bearer service-token")).thenReturn(true);
        when(receipts.accept(any(), eq("Bearer service-token")))
                .thenReturn(new ActionReceiptService.Acceptance("APPLIED", "SUCCEEDED", false));
        var mvc = MockMvcBuilders.standaloneSetup(new ActionInternalController(service, receipts))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        mvc.perform(post("/incident/internal/v1/action-receipts").contentType("application/json").content("{bad"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/incident/internal/v1/action-receipts")
                        .header("Authorization", "Bearer service-token")
                        .contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/incident/internal/v1/action-receipts")
                        .header("Authorization", "Bearer service-token")
                        .contentType("application/json").content("""
                        {"schemaVersion":1,"receiptId":"400","actionId":"200","incidentId":"100",
                         "executionId":"300","executionKey":"key","requestId":"req",
                         "toolName":"simulation.set-state","toolVersion":"1",
                         "target":{"type":"SIM_SERVICE","id":"demo"},"resultStatus":"SUCCESS",
                         "resultCode":"SIMULATION_APPLIED","occurredAt":"2026-09-28T12:00:00Z",
                         "summary":{"desiredState":"PAUSED","applied":true}}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.disposition").value("APPLIED"))
                .andExpect(jsonPath("$.data.ticket").doesNotExist());
    }

    private static void assertTrueCallbackRoute() {
        var callback = new MockHttpServletRequest("POST", "/incident/internal/v1/action-tickets/consume");
        org.junit.jupiter.api.Assertions.assertTrue(SaTokenConfig.isIncidentLocalAuthRoute(callback));
        org.junit.jupiter.api.Assertions.assertFalse(SaTokenConfig.isIncidentLocalAuthRoute(
                new MockHttpServletRequest("GET", "/incident/internal/v1/action-tickets/consume")));
    }
}
