package com.nageoffer.ai.ragent.incident.action.service;

import com.nageoffer.ai.ragent.incident.action.contract.ActionBindingV1;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

/** One-time online authorization checked in the Incident transaction, never from an MCP self-claim. */
@Service
public final class ActionTicketConsumptionService {
    private final ActionMapper actions;
    private final ApprovalMapper approvals;
    private final ExecutionMapper executions;
    private final TicketMapper tickets;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final AlertMapper alerts;
    private final ActionServiceAuthentication authentication;

    public ActionTicketConsumptionService(ActionMapper actions, ApprovalMapper approvals,
            ExecutionMapper executions, TicketMapper tickets, ActionTransactionGuard guard,
            ActionTimelineWriter timeline, AlertMapper alerts, ActionServiceAuthentication authentication) {
        this.actions = actions;
        this.approvals = approvals;
        this.executions = executions;
        this.tickets = tickets;
        this.guard = guard;
        this.timeline = timeline;
        this.alerts = alerts;
        this.authentication = authentication;
    }

    public ConsumeResult consume(ConsumeRequest request, String authorization) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.ticket.consume", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        if (!authentication.isAuthenticated(authorization)) throw new UnauthorizedException();
        if (request == null || request.ticket() == null || !request.ticket().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid ticket");
        }
        String canonical = ActionBindingV1.canonicalParameters(request.parametersJson());
        var candidate = actions.findById(request.actionId());
        if (candidate == null || !candidate.getIncidentId().equals(request.incidentId())) {
            throw new DeniedException();
        }
        return guard.inIncident(candidate.getIncidentId(), locked -> {
            var action = actions.lockById(request.actionId());
            var approval = approvals.lockByActionId(request.actionId());
            var execution = executions.findByActionId(request.actionId());
            var ticket = tickets.lockByDigest(sha256(request.ticket()));
            if (action == null || approval == null || execution == null || ticket == null
                    || !"DISPATCHING".equals(action.getState())
                    || !"APPROVED".equals(approval.getStatus())
                    || "CLOSED".equals(locked.row().getLifecycleState())
                    || !execution.getId().equals(request.executionId())
                    || !ticket.getExecutionId().equals(execution.getId())
                    || !execution.getDispatchRequestId().equals(request.requestId())
                    || !execution.getExecutionKey().equals(request.executionKey())
                    || !action.getExecutionKey().equals(request.executionKey())
                    || !ActionBindingV1.VERSION.equals(request.bindingVersion())
                    || !action.getToolName().equals(request.toolName())
                    || !action.getToolVersion().equals(request.toolVersion())
                    || !action.getTargetType().equals(request.targetType())
                    || !action.getTargetId().equals(request.targetId())
                    || !ActionBindingV1.canonicalParameters(action.getParametersJson()).equals(canonical)
                    || !execution.getBindingDigest().equals(action.getBindingDigest())
                    || !approval.getBindingDigest().equals(action.getBindingDigest())
                    || !action.getBindingDigest().equals(ActionBindingV1.bindingDigest(action.getId(),
                            action.getIncidentId(), request.toolName(), request.toolVersion(),
                            request.targetType(), request.targetId(), canonical, request.executionKey()))) {
                throw new DeniedException();
            }
            com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(action);
            observation.id("executionId", execution.getId()).id("dispatchRequestId", execution.getDispatchRequestId());
            var now = locked.now();
            if (!"ISSUED".equals(ticket.getStatus()) || !now.isBefore(ticket.getExpiresAt())
                    || action.getAuthorizationDueAt() == null
                    || !now.isBefore(action.getAuthorizationDueAt())
                    || approval.getAuthorizationDueAt() == null
                    || !now.isBefore(approval.getAuthorizationDueAt())) {
                throw new DeniedException();
            }
            var due = now.plusNanos((execution.getMaxExecutionDurationMs()
                    + execution.getReceiptGracePeriodMs()) * 1_000_000);
            if (tickets.consume(ticket.getId(), now) != 1
                    || executions.markStarted(execution.getId(), now, due) != 1
                    || actions.transition(action.getId(), "DISPATCHING", action.getVersion(),
                            "EXECUTING", null, null) != 1) throw new DeniedException();
            var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
            if (anchor == null) throw new IllegalStateException("Incident anchor missing");
            timeline.ticketConsumed(action.getIncidentId(), locked.reserveTimeline(1),
                    execution.getId(), ticket.getId(), anchor.getCorrelationId(), now);
            observation.outcome("GRANTED");
            return new ConsumeResult("GRANTED", due, execution.getMaxExecutionDurationMs());
        });

        }
    }

    public boolean isAuthenticated(String authorization) {
        return authentication.isAuthenticated(authorization);
    }

    private static String sha256(String hexTicket) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(HexFormat.of().parseHex(hexTicket)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record ConsumeRequest(String ticket, String actionId, String incidentId, String executionId,
            String executionKey, String requestId, String bindingVersion, String toolName,
            String toolVersion, String targetType, String targetId, String parametersJson) {
        @Override
        public String toString() { return "ConsumeRequest[actionId=" + actionId + "]"; }
    }
    public record ConsumeResult(String decision, java.time.OffsetDateTime receiptDueAt,
            long maxExecutionDurationMs) {}
    public static final class UnauthorizedException extends RuntimeException {}
    public static final class DeniedException extends RuntimeException {}
}
