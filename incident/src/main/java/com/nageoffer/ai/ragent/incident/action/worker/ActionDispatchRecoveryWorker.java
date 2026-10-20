package com.nageoffer.ai.ragent.incident.action.worker;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.incident.action.config.ActionProperties;
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

/** Replaces a lost plaintext ticket only after proving the execution has not started. */
@Component
@ConditionalOnProperty(prefix = "incident.action.worker", name = "enabled", havingValue = "true")
public final class ActionDispatchRecoveryWorker {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ActionMapper actions;
    private final ApprovalMapper approvals;
    private final ExecutionMapper executions;
    private final TicketMapper tickets;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final AlertMapper alerts;
    private final ActionProperties properties;
    private ActionDispatchWorker.DispatchPort port;

    public ActionDispatchRecoveryWorker(ActionMapper actions, ApprovalMapper approvals,
            ExecutionMapper executions, TicketMapper tickets, ActionTransactionGuard guard,
            ActionTimelineWriter timeline, AlertMapper alerts, ActionProperties properties) {
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
    public void setDispatchPort(ActionDispatchWorker.DispatchPort port) {
        this.port = port;
    }

    @Scheduled(fixedDelayString = "${incident.action.worker.poll-delay-ms:1000}")
    public void recoverDue() {
        if (port == null) throw new IllegalStateException("protected dispatch port unavailable");
        for (var candidate : actions.findRecoveryCandidates(100)) {
            try (var perRecord = com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.action.recover")) {
            com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
            var claim = recover(candidate.getId());
            if (claim != null) port.dispatch(claim);

            }
        }
    }

    public ActionDispatchWorker.DispatchClaim recover(String actionId) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.action.recover", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        var candidate = actions.findById(actionId);
        if (candidate == null) return null;
        com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
        return guard.inIncident(candidate.getIncidentId(), locked -> {
            var action = actions.lockById(actionId);
            if (action == null || !"DISPATCHING".equals(action.getState())
                    || "CLOSED".equals(locked.row().getLifecycleState())) return null;
            var execution = executions.findByActionId(actionId);
            if (execution == null || execution.getExecutionStartedAt() != null
                    || tickets.countConsumed(execution.getId()) != 0
                    || !execution.getExecutionKey().equals(action.getExecutionKey())
                    || !execution.getBindingDigest().equals(action.getBindingDigest())) return null;
            var approval = approvals.lockByActionId(actionId);
            if (approval == null || !"APPROVED".equals(approval.getStatus())
                    || !approval.getBindingDigest().equals(action.getBindingDigest())) return null;
            var now = locked.now();
            observation.id("executionId", execution.getId()).id("dispatchRequestId", execution.getDispatchRequestId());
            var old = tickets.lockIssued(execution.getId());
            var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
            if (anchor == null) throw new IllegalStateException("Incident anchor missing");
            var due = action.getAuthorizationDueAt();
            if (due == null || approval.getAuthorizationDueAt() == null
                    || approval.getAuthorizationDueAt().isBefore(due)) due = approval.getAuthorizationDueAt();
            if (due == null || !now.isBefore(due)) {
                if (old != null) {
                    if (tickets.expire(old.getId(), "AUTHORIZATION_DEADLINE", null) != 1)
                        throw new IllegalStateException("ticket expiry conflict");
                    timeline.ticketExpired(action.getIncidentId(), locked.reserveTimeline(1),
                            execution.getId(), old.getId(), "AUTHORIZATION_DEADLINE",
                            anchor.getCorrelationId(), now);
                }
                if (actions.transition(actionId, "DISPATCHING", action.getVersion(), "EXPIRED",
                        null, "AUTHORIZATION_DEADLINE") != 1)
                    throw new IllegalStateException("authorization expiry conflict");
                timeline.statusChanged(action.getIncidentId(), locked.reserveTimeline(1), actionId,
                        "EXPIRED", "AUTHORIZATION_DEADLINE", anchor.getCorrelationId(), now);
                return null;
            }
            String replacementId = IdUtil.getSnowflakeNextIdStr();
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            String ticket = HexFormat.of().formatHex(secret);
            OffsetDateTime expires = now.plus(properties.getTicketTtl());
            if (expires.isAfter(due)) expires = due;
            if (old != null) {
                if (tickets.expire(old.getId(), "RECOVERY_REPLACED", null) != 1)
                    throw new IllegalStateException("ticket replacement conflict");
                timeline.ticketExpired(action.getIncidentId(), locked.reserveTimeline(1),
                        execution.getId(), old.getId(), "RECOVERY_REPLACED",
                        anchor.getCorrelationId(), now);
            }
            var issued = new TicketMapper.NewTicket();
            issued.setId(replacementId);
            issued.setExecutionId(execution.getId());
            issued.setSecretDigest(sha256(secret));
            issued.setIssuedAt(now);
            issued.setExpiresAt(expires);
            tickets.insertIssued(issued);
            if (old != null && tickets.linkReplacement(old.getId(), replacementId) != 1)
                throw new IllegalStateException("replacement link conflict");
            timeline.ticketIssued(action.getIncidentId(), locked.reserveTimeline(1),
                    execution.getId(), replacementId, anchor.getCorrelationId(), now);
            return new ActionDispatchWorker.DispatchClaim(actionId, action.getIncidentId(),
                    execution.getId(), execution.getExecutionKey(), execution.getDispatchRequestId(),
                    ticket, action.getBindingVersion(), execution.getToolName(),
                    execution.getToolVersion(), execution.getTargetId(), action.getParametersJson());
        });

        }
    }

    private static String sha256(byte[] secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
