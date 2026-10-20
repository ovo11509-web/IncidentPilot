package com.nageoffer.ai.ragent.incident.action.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionQueryService;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionSecretLeakTest {
    @Test
    void queryAndLocalEvidenceExcludeSecretsAndRemotePlainHttpFailsClosed() throws Exception {
        var actions = mock(ActionMapper.class);
        var approvals = mock(ApprovalMapper.class);
        var executions = mock(ExecutionMapper.class);
        var tickets = mock(TicketMapper.class);
        var receipts = mock(ReceiptMapper.class);
        var incidents = mock(IncidentMapper.class);
        var timeline = mock(TimelineMapper.class);
        var action = new ActionMapper.ActionRow();
        action.setId("200"); action.setIncidentId("100"); action.setState("EXECUTING");
        action.setParametersJson("{\"desiredState\":\"PAUSED\",\"untrusted\":\"RAW-MARKER\"}");
        when(actions.findById("200")).thenReturn(action);
        var incident = new IncidentMapper.IncidentRow(); incident.setId("100");
        when(incidents.findById("100")).thenReturn(incident);
        var approval = new ApprovalMapper.ApprovalRow();
        approval.setStatus("APPROVED"); approval.setApprovalDueAt(OffsetDateTime.now());
        when(approvals.findByActionId("200")).thenReturn(approval);
        var execution = new ExecutionMapper.ExecutionRow();
        execution.setId("300"); execution.setExecutionKey("execution-key");
        when(executions.findByActionId("200")).thenReturn(execution);
        var ticket = new TicketMapper.TicketRow();
        ticket.setId("400"); ticket.setStatus("CONSUMED");
        ticket.setSecretDigest("DIGEST-MARKER");
        ticket.setIssuedAt(OffsetDateTime.now()); ticket.setExpiresAt(OffsetDateTime.now());
        when(tickets.findByExecutionId("300")).thenReturn(List.of(ticket));
        when(receipts.findByExecutionId("300")).thenReturn(List.of());
        when(timeline.findActionFacts("100", "200", "300")).thenReturn(List.of());
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            String response = new ActionQueryService(actions, approvals, executions, tickets,
                    receipts, incidents, timeline, mock(org.springframework.transaction.PlatformTransactionManager.class)).get("200").toString();
            assertFalse(response.contains("RAW-MARKER"));
            assertFalse(response.contains("DIGEST-MARKER"));
            assertFalse(response.contains("Authorization"));
        }
        assertThrows(IllegalArgumentException.class, () -> ActionServiceAuthentication.requireSafeEndpoint(
                URI.create("http://mcp.example.test/mcp/incident")));
        assertTrue(ActionServiceAuthentication.isSensitiveHeader("Authorization"));
        assertTrue(ActionServiceAuthentication.isSensitiveHeader("X-Action-Ticket"));

        var log = Path.of("target", "protected-simulation-process.log");
        if (Files.isRegularFile(log)) {
            String evidence = Files.readString(log);
            assertFalse(evidence.contains("isolated-outbound-token-123456789012345"));
            assertFalse(evidence.contains("isolated-callback-token-123456789012345"));
        }
    }
}
