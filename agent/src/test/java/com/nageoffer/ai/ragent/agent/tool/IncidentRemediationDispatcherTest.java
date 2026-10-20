package com.nageoffer.ai.ragent.agent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nageoffer.ai.ragent.incident.action.config.ActionProperties;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IncidentRemediationDispatcherTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final ActionDispatchWorker.DispatchClaim CLAIM = new ActionDispatchWorker.DispatchClaim(
            "200", "100", "300", "execution-key", "dispatch-request", SECRET,
            "action-binding-v1", "simulation.set-state", "1", "demo",
            "{\"desiredState\":\"PAUSED\"}");

    @Test
    void consumedOrUncertainTicketIsNeverCalledAgain() {
        var tickets = mock(TicketMapper.class);
        var receipts = mock(ActionReceiptService.class);
        var calls = new AtomicInteger();
        IncidentRemediationDispatcher.ProtectedCall failing = (arguments, meta) -> {
            calls.incrementAndGet();
            throw new IllegalStateException("network response lost");
        };
        new IncidentRemediationDispatcher(tickets, receipts, new ActionProperties(), failing).dispatch(CLAIM);
        assertEquals(1, calls.get());
        verify(tickets).canRetryBeforeConsumption("300",
                "4884fdaafea47c29fea7159d0daddd9c085d6200e1359e85bb81736af6b7c837");
        verify(receipts, never()).acceptFromWorker(any());
        assertFalse(CLAIM.toString().contains(SECRET));
    }

    @Test
    void provenIssuedTicketMayRetryWithIdenticalRequestIdentity() {
        var tickets = mock(TicketMapper.class);
        var receipts = mock(ActionReceiptService.class);
        when(tickets.canRetryBeforeConsumption(anyString(), anyString())).thenReturn(true);
        var calls = new AtomicInteger();
        IncidentRemediationDispatcher.ProtectedCall call = (arguments, meta) -> {
            assertEquals(java.util.Set.of("actionId", "incidentId", "executionId", "executionKey",
                    "requestId", "ticket", "bindingVersion"), meta.keySet());
            assertEquals("dispatch-request", meta.get("requestId"));
            assertEquals("execution-key", meta.get("executionKey"));
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("pre-consumption loss");
            return CallToolResult.builder().isError(true).build();
        };
        new IncidentRemediationDispatcher(tickets, receipts, new ActionProperties(), call).dispatch(CLAIM);
        assertEquals(2, calls.get());
    }

    @Test
    void structuredResponseUsesTrustedInProcessReceiptPath() {
        var tickets = mock(TicketMapper.class);
        var receipts = mock(ActionReceiptService.class);
        var structured = Map.<String, Object>ofEntries(
                Map.entry("schemaVersion", 1), Map.entry("receiptId", "400"),
                Map.entry("actionId", "200"), Map.entry("incidentId", "100"),
                Map.entry("executionId", "300"), Map.entry("executionKey", "execution-key"),
                Map.entry("requestId", "dispatch-request"), Map.entry("toolName", "simulation.set-state"),
                Map.entry("toolVersion", "1"), Map.entry("target", Map.of("type", "SIM_SERVICE", "id", "demo")),
                Map.entry("resultStatus", "SUCCESS"), Map.entry("resultCode", "SIMULATION_APPLIED"),
                Map.entry("occurredAt", "2026-09-28T12:00:00Z"),
                Map.entry("summary", Map.of("desiredState", "PAUSED", "applied", true)));
        IncidentRemediationDispatcher.ProtectedCall call = (arguments, meta) ->
                CallToolResult.builder().structuredContent(structured).isError(false).build();
        new IncidentRemediationDispatcher(tickets, receipts, new ActionProperties(), call).dispatch(CLAIM);
        verify(receipts).acceptFromWorker(any(ActionReceiptService.Submission.class));
    }
}
