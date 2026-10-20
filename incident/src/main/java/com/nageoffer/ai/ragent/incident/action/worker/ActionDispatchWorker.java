package com.nageoffer.ai.ragent.incident.action.worker;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.incident.action.config.ActionProperties;
import com.nageoffer.ai.ragent.incident.action.contract.ActionBindingV1;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims one approved Action under the Incident lock, commits, then invokes the outbound port. */
@Component
@ConditionalOnProperty(prefix = "incident.action.worker", name = "enabled", havingValue = "true")
public final class ActionDispatchWorker {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ActionMapper actions;
    private final ApprovalMapper approvals;
    private final ExecutionMapper executions;
    private final TicketMapper tickets;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final AlertMapper alerts;
    private final ActionProperties properties;
    private DispatchPort port;

    public ActionDispatchWorker(ActionMapper actions, ApprovalMapper approvals, ExecutionMapper executions,
            TicketMapper tickets, ActionTransactionGuard guard, ActionTimelineWriter timeline,
            AlertMapper alerts, ActionProperties properties) {
        this.actions = actions;
        this.approvals = approvals;
        this.executions = executions;
        this.tickets = tickets;
        this.guard = guard;
        this.timeline = timeline;
        this.alerts = alerts;
        this.properties = properties;
    }

    @Autowired(required = false)
    public void setDispatchPort(DispatchPort port) {
        this.port = port;
    }

    @Scheduled(fixedDelayString = "${incident.action.worker.poll-delay-ms:1000}")
    public void dispatchDue() {
        if (port == null) throw new IllegalStateException("protected dispatch port unavailable");
        for (var candidate : actions.findDispatchCandidates(100)) {
            try (var perRecord = com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.action.dispatch")) {
            com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
            try {
                port.dispatch(claim(candidate.getId()));
            } catch (ConflictException ignored) {
                // Another worker or expiry won; no network call was made for this candidate.
            }

            }
        }
    }

    public DispatchClaim claim(String actionId) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.action.dispatch", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        var candidate = actions.findById(actionId);
        if (candidate == null) throw new ConflictException();
        com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
        var claimed = guard.inIncident(candidate.getIncidentId(), locked -> {
            var action = actions.lockById(actionId);
            if (action == null || !"APPROVED".equals(action.getState())
                    || "CLOSED".equals(locked.row().getLifecycleState())) throw new ConflictException();
            var approval = approvals.lockByActionId(actionId);
            if (approval == null || !"APPROVED".equals(approval.getStatus())
                    || !action.getBindingDigest().equals(approval.getBindingDigest())
                    || !action.getBindingDigest().equals(ActionBindingV1.bindingDigest(action.getId(),
                            action.getIncidentId(), action.getToolName(), action.getToolVersion(),
                            action.getTargetType(), action.getTargetId(), action.getParametersJson(),
                            action.getExecutionKey()))) throw new ConflictException();
            OffsetDateTime now = locked.now();
            if (action.getAuthorizationDueAt() == null || approval.getAuthorizationDueAt() == null
                    || !now.isBefore(action.getAuthorizationDueAt())
                    || !now.isBefore(approval.getAuthorizationDueAt())) throw new ConflictException();
            if (executions.findByActionId(actionId) != null) throw new ConflictException();
            String executionId = IdUtil.getSnowflakeNextIdStr();
            String ticketId = IdUtil.getSnowflakeNextIdStr();
            String requestId = IdUtil.getSnowflakeNextIdStr();
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            String ticket = HexFormat.of().formatHex(secret);
            var expiry = now.plus(properties.getTicketTtl());
            if (expiry.isAfter(action.getAuthorizationDueAt())) expiry = action.getAuthorizationDueAt();
            if (expiry.isAfter(approval.getAuthorizationDueAt())) expiry = approval.getAuthorizationDueAt();
            if (!expiry.isAfter(now)) throw new ConflictException();
            var execution = new ExecutionMapper.NewExecution();
            execution.setId(executionId);
            execution.setActionId(actionId);
            execution.setExecutionKey(action.getExecutionKey());
            execution.setDispatchRequestId(requestId);
            execution.setToolName(action.getToolName());
            execution.setToolVersion(action.getToolVersion());
            execution.setTargetType(action.getTargetType());
            execution.setTargetId(action.getTargetId());
            execution.setBindingDigest(action.getBindingDigest());
            execution.setMaxExecutionDurationMs(properties.getMaxExecutionDuration().toMillis());
            execution.setReceiptGracePeriodMs(properties.getReceiptGracePeriod().toMillis());
            executions.insert(execution);
            var issued = new TicketMapper.NewTicket();
            issued.setId(ticketId);
            issued.setExecutionId(executionId);
            issued.setSecretDigest(sha256(secret));
            issued.setIssuedAt(now);
            issued.setExpiresAt(expiry);
            tickets.insertIssued(issued);
            if (actions.transition(actionId, "APPROVED", action.getVersion(),
                    "DISPATCHING", null, null) != 1) throw new ConflictException();
            var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
            if (anchor == null) throw new IllegalStateException("Incident anchor missing");
            long sequence = locked.reserveTimeline(2);
            timeline.dispatched(action.getIncidentId(), sequence, actionId, executionId,
                    anchor.getCorrelationId(), now);
            timeline.ticketIssued(action.getIncidentId(), sequence + 1, executionId, ticketId,
                    anchor.getCorrelationId(), now);
            return new DispatchClaim(actionId, action.getIncidentId(), executionId,
                    action.getExecutionKey(), requestId, ticket, action.getBindingVersion(),
                    action.getToolName(), action.getToolVersion(), action.getTargetId(),
                    action.getParametersJson());
        });
        com.nageoffer.ai.ragent.incident.observability.IncidentObservation.dispatch(claimed);
        observation.outcome("DISPATCHING");
        return claimed;
        }
    }

    private static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public interface DispatchPort {
        void dispatch(DispatchClaim claim);
    }

    /** Ticket is process memory only and must never be logged, serialized as evidence or persisted. */
    public record DispatchClaim(String actionId, String incidentId, String executionId,
            String executionKey, String requestId, String ticket, String bindingVersion,
            String toolName, String toolVersion, String targetId, String parametersJson) {
        @Override
        public String toString() {
            return "DispatchClaim[actionId=" + actionId + ",executionId=" + executionId + "]";
        }
    }

    public static final class ConflictException extends RuntimeException {}
}
