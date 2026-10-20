package com.nageoffer.ai.ragent.incident.action.service;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.config.ActionProperties;
import com.nageoffer.ai.ragent.incident.action.contract.ActionBindingV1;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionOutboxWriter;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import org.springframework.stereotype.Service;

/** Personnel decision is checked after row locks against the database clock. */
@Service
public final class ActionDecisionService {
    private final ActionMapper actions;
    private final ApprovalMapper approvals;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final ActionOutboxWriter outbox;
    private final ActionProperties properties;
    private final AlertMapper alerts;

    public ActionDecisionService(ActionMapper actions, ApprovalMapper approvals, ActionTransactionGuard guard,
            ActionTimelineWriter timeline, ActionOutboxWriter outbox, ActionProperties properties,
            AlertMapper alerts) {
        this.actions = actions;
        this.approvals = approvals;
        this.guard = guard;
        this.timeline = timeline;
        this.outbox = outbox;
        this.properties = properties;
        this.alerts = alerts;
    }

    public ActionMapper.ActionRow decide(String actionId, long expectedVersion, String decision,
            String reason, String correlationId) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.action.decide", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
        String actorId = StpUtil.getLoginIdAsString();
        if (expectedVersion < 0 || !("APPROVE".equals(decision) || "REJECT".equals(decision))) {
            throw new IllegalArgumentException("invalid decision");
        }
        if ("REJECT".equals(decision) && (reason == null || reason.isBlank() || reason.length() > 512)) {
            throw new IllegalArgumentException("rejection reason required");
        }
        var candidate = actions.findById(actionId);
        if (candidate == null) throw new NotFoundException();
        com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
        var result = guard.inIncident(candidate.getIncidentId(), locked -> {
            var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
            if (anchor == null) throw new IllegalStateException("Incident anchor missing");
            String trustedCorrelation = anchor.getCorrelationId();
            var action = actions.lockById(actionId);
            var approval = approvals.lockByActionId(actionId);
            if (action == null || approval == null || !action.getIncidentId().equals(candidate.getIncidentId())
                    || !"PENDING_APPROVAL".equals(action.getState()) || action.getVersion() != expectedVersion
                    || !"PENDING".equals(approval.getStatus()) || approval.getVersion() != expectedVersion
                    || !action.getBindingDigest().equals(approval.getBindingDigest())
                    || !ActionBindingV1.VERSION.equals(action.getBindingVersion())
                    || !action.getBindingDigest().equals(ActionBindingV1.bindingDigest(action.getId(),
                            action.getIncidentId(), action.getToolName(), action.getToolVersion(),
                            action.getTargetType(), action.getTargetId(), action.getParametersJson(),
                            action.getExecutionKey()))
                    || "CLOSED".equals(locked.row().getLifecycleState())) {
                throw new ConflictException();
            }
            var now = locked.now();
            if (!now.isBefore(action.getApprovalDueAt()) || !now.isBefore(approval.getApprovalDueAt())) {
                if (actions.transition(actionId, "PENDING_APPROVAL", expectedVersion,
                        "EXPIRED", null, "APPROVAL_DEADLINE") != 1
                        || approvals.decide(actionId, approval.getVersion(), "EXPIRED", null,
                                null, now, null, "APPROVAL_DEADLINE", null) != 1) {
                    throw new ConflictException();
                }
                timeline.statusChanged(action.getIncidentId(), locked.reserveTimeline(1), actionId,
                        "EXPIRED", "APPROVAL_DEADLINE", trustedCorrelation, now);
                return new DecisionResult(null, true);
            }
            String status = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
            String state = "APPROVE".equals(decision) ? "APPROVED" : "APPROVAL_REJECTED";
            var authDue = "APPROVE".equals(decision) ? now.plus(properties.getAuthorizationTtl()) : null;
            String reasonCode = "APPROVE".equals(decision) ? null : "PERSONNEL_REJECTED";
            if (actions.transition(actionId, "PENDING_APPROVAL", expectedVersion,
                    state, authDue, reasonCode) != 1
                    || approvals.decide(actionId, approval.getVersion(), status, actorId, "admin",
                            now, authDue, reasonCode,
                            "REJECT".equals(decision) ? "Personnel declined simulation" : null) != 1) {
                throw new ConflictException();
            }
            var updatedAction = actions.findById(actionId);
            var updatedApproval = approvals.findByActionId(actionId);
            observation.id("approvalId", updatedApproval.getId());
            var fact = timeline.approvalDecided(action.getIncidentId(), locked.reserveTimeline(1),
                    actionId, approval.getId(), status, actorId, trustedCorrelation, now);
            outbox.approvalDecided(fact, updatedAction, updatedApproval, locked.row().getVersion(),
                    trustedCorrelation, now);
            return new DecisionResult(updatedAction, false);
        });
        if (result.expired()) throw new ConflictException();
        observation.outcome(result.action() == null ? "EXPIRED" : result.action().getState());
        return result.action();

        }
    }

    private record DecisionResult(ActionMapper.ActionRow action, boolean expired) {}
    public static final class ConflictException extends RuntimeException {}
    public static final class NotFoundException extends RuntimeException {}
}
