package com.nageoffer.ai.ragent.incident.action.worker;

import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Moves overdue, still executing actions to UNKNOWN; only a read-only port may follow. */
@Component
@ConditionalOnProperty(prefix = "incident.action.worker", name = "enabled", havingValue = "true")
public final class ActionReceiptDeadlineWorker {
    private final ActionMapper actions;
    private final ExecutionMapper executions;
    private final ReceiptMapper receipts;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final AlertMapper alerts;
    private ReconciliationPort reconciliation;

    public ActionReceiptDeadlineWorker(ActionMapper actions, ExecutionMapper executions,
            ReceiptMapper receipts, ActionTransactionGuard guard, ActionTimelineWriter timeline,
            AlertMapper alerts) {
        this.actions = actions;
        this.executions = executions;
        this.receipts = receipts;
        this.guard = guard;
        this.timeline = timeline;
        this.alerts = alerts;
    }

    @Autowired(required = false)
    public void setReconciliationPort(ReconciliationPort reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(fixedDelayString = "${incident.action.worker.poll-delay-ms:1000}")
    public int sweepDue() {
        int changed = 0;
        for (var candidate : actions.findReceiptDeadlineCandidates(100)) {
            try (var perRecord = com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.action.sweep")) {
            com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
            String executionId = guard.inIncident(candidate.getIncidentId(), locked -> {
                var action = actions.lockById(candidate.getId());
                var execution = executions.findByActionId(candidate.getId());
                if (action == null || !"EXECUTING".equals(action.getState())
                        || execution == null || execution.getReceiptDueAt() == null
                        || locked.now().isBefore(execution.getReceiptDueAt())
                        || execution.getCurrentReceiptId() != null
                        || receipts.findByExecutionId(execution.getId()).stream().anyMatch(row ->
                                "APPLIED".equals(row.getDisposition())
                                        && !"UNKNOWN".equals(row.getResultStatus()))) return null;
                if (actions.transition(action.getId(), "EXECUTING", action.getVersion(),
                        "UNKNOWN", null, "RECEIPT_DEADLINE") != 1)
                    throw new IllegalStateException("receipt deadline conflict");
                var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
                if (anchor == null) throw new IllegalStateException("Incident anchor missing");
                timeline.statusChanged(action.getIncidentId(), locked.reserveTimeline(1),
                        action.getId(), "UNKNOWN", "RECEIPT_DEADLINE",
                        anchor.getCorrelationId(), locked.now());
                return execution.getId();
            });
            if (executionId != null) {
                changed++;
                if (reconciliation != null) reconciliation.requestReadOnly(executionId);
            }

            }
        }
        return changed;
    }

    public interface ReconciliationPort {
        void requestReadOnly(String executionId);
    }
}
