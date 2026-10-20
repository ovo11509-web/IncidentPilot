package com.nageoffer.ai.ragent.incident.action.worker;

import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expires only actions that have not started dispatch; all checks repeat after the Incident lock. */
@Component
@ConditionalOnProperty(prefix = "incident.action.worker", name = "enabled", havingValue = "true")
public final class ActionExpiryWorker {
    private final ActionMapper actions;
    private final ApprovalMapper approvals;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final AlertMapper alerts;

    public ActionExpiryWorker(ActionMapper actions, ApprovalMapper approvals, ActionTransactionGuard guard,
            ActionTimelineWriter timeline, AlertMapper alerts) {
        this.actions = actions;
        this.approvals = approvals;
        this.guard = guard;
        this.timeline = timeline;
        this.alerts = alerts;
    }

    @Scheduled(fixedDelayString = "${incident.action.worker.poll-delay-ms:1000}")
    public int expireDue() {
        int changed = 0;
        for (var candidate : actions.findDue(100)) {
            try (var observation = com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.action.sweep")) {
            com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
            changed += guard.inIncident(candidate.getIncidentId(), locked -> {
                var action = actions.lockById(candidate.getId());
                if (action == null || !("PENDING_APPROVAL".equals(action.getState())
                        || "APPROVED".equals(action.getState()))) return 0;
                var now = locked.now();
                var due = "PENDING_APPROVAL".equals(action.getState())
                        ? action.getApprovalDueAt() : action.getAuthorizationDueAt();
                if (due == null || now.isBefore(due)) return 0;
                String reason = "PENDING_APPROVAL".equals(action.getState())
                        ? "APPROVAL_DEADLINE" : "AUTHORIZATION_DEADLINE";
                if (actions.transition(action.getId(), action.getState(), action.getVersion(),
                        "EXPIRED", null, reason) != 1) throw new IllegalStateException("expiry conflict");
                if ("PENDING_APPROVAL".equals(action.getState())) {
                    var approval = approvals.lockByActionId(action.getId());
                    if (approval == null || approvals.decide(action.getId(), approval.getVersion(),
                            "EXPIRED", null, null, now, null, reason, null) != 1) {
                        throw new IllegalStateException("approval expiry conflict");
                    }
                }
                var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
                if (anchor == null) throw new IllegalStateException("Incident anchor missing");
                timeline.statusChanged(action.getIncidentId(), locked.reserveTimeline(1), action.getId(),
                        "EXPIRED", reason, anchor.getCorrelationId(), now);
                observation.outcome("EXPIRED");
                return 1;
            });
            }
        }
        return changed;
    }
}
