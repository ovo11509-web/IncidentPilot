package com.nageoffer.ai.ragent.incident.action.service;

import com.nageoffer.ai.ragent.incident.action.contract.ActionBindingV1;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.action.worker.ActionReceiptDeadlineWorker;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Accepts only bound, read-only simulation evidence under the Incident lock. */
@Service
public final class ActionReconciliationService implements ActionReceiptDeadlineWorker.ReconciliationPort {
    private final ActionMapper actions;
    private final ExecutionMapper executions;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final AlertMapper alerts;
    private final EvidencePort evidencePort;

    public ActionReconciliationService(ActionMapper actions, ExecutionMapper executions,
            ActionTransactionGuard guard, ActionTimelineWriter timeline, AlertMapper alerts,
            EvidencePort evidencePort) {
        this.actions = actions;
        this.executions = executions;
        this.guard = guard;
        this.timeline = timeline;
        this.alerts = alerts;
        this.evidencePort = evidencePort;
    }

    @Override
    public void requestReadOnly(String executionId) {
        var execution = executions.findById(executionId);
        if (execution != null) reconcile(execution.getActionId());
    }

    public String reconcile(String actionId) {
        return reconcileObserved(actionId, null);
    }

    public String reconcile(String actionId, long expectedVersion) {
        return reconcileObserved(actionId, Long.valueOf(expectedVersion));
    }

    private String reconcileObserved(String actionId, Long expectedVersion) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.action.reconcile", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        var candidate = actions.findById(actionId);
        var execution = executions.findByActionId(actionId);
        if (candidate == null || execution == null || execution.getExecutionStartedAt() == null) {
            throw new IllegalArgumentException("Action execution unavailable");
        }
        if (expectedVersion != null && candidate.getVersion() != expectedVersion)
            throw new IllegalStateException("reconciliation version conflict");
        com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
        observation.id("executionId", execution.getId()).id("dispatchRequestId", execution.getDispatchRequestId());
        Evidence evidence;
        try {
            evidence = evidencePort.read(execution.getId(), execution.getExecutionKey(),
                    execution.getTargetId());
        } catch (RuntimeException unavailable) {
            return actions.findById(actionId).getState();
        }
        if (!valid(evidence, candidate, execution)) return actions.findById(actionId).getState();
        if ("INDETERMINATE".equals(evidence.status())) return actions.findById(actionId).getState();
        return guard.inIncident(candidate.getIncidentId(), locked -> {
            var action = actions.lockById(actionId);
            var current = executions.findByActionId(actionId);
            if (action == null || current == null || !valid(evidence, action, current))
                throw new IllegalStateException("reconciliation binding changed");
            if (expectedVersion != null && action.getVersion() != expectedVersion)
                throw new IllegalStateException("reconciliation version conflict");
            String next = "APPLIED".equals(evidence.status()) ? "SUCCEEDED" : "FAILED";
            boolean applied = "UNKNOWN".equals(action.getState());
            boolean conflict = !applied && !next.equals(action.getState());
            if (!applied && !conflict) return action.getState();
            if (applied && actions.transition(actionId, "UNKNOWN", action.getVersion(), next,
                    null, "RECONCILIATION_" + evidence.status()) != 1) {
                throw new IllegalStateException("reconciliation state conflict");
            }
            var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
            if (anchor == null) throw new IllegalStateException("Incident anchor missing");
            timeline.reconciliation(action.getIncidentId(), locked.reserveTimeline(1), current.getId(),
                    evidence.status(), conflict ? "CONFLICT" : "APPLIED", evidence.evidenceRef(),
                    anchor.getCorrelationId(), locked.now());
            return applied ? next : action.getState();
        });

        }
    }

    private static boolean valid(Evidence evidence, ActionMapper.ActionRow action,
            ExecutionMapper.ExecutionRow execution) {
        if (evidence == null || evidence.status() == null
                || !Set.of("APPLIED", "PROVEN_NOT_APPLIED", "INDETERMINATE")
                .contains(evidence.status()) || !execution.getId().equals(evidence.executionId())
                || !execution.getExecutionKey().equals(evidence.executionKey())
                || !execution.getTargetId().equals(evidence.targetId())
                || !action.getBindingDigest().equals(execution.getBindingDigest())
                || !action.getBindingDigest().equals(ActionBindingV1.bindingDigest(action.getId(),
                        action.getIncidentId(), action.getToolName(), action.getToolVersion(),
                        action.getTargetType(), action.getTargetId(), action.getParametersJson(),
                        action.getExecutionKey()))) return false;
        if ("INDETERMINATE".equals(evidence.status())) return true;
        if ("APPLIED".equals(evidence.status())) {
            String desired = ActionBindingV1.canonicalParameters(action.getParametersJson());
            return evidence.beforeState() != null && evidence.afterState() != null
                    && Set.of("READY", "PAUSED").contains(evidence.beforeState())
                    && Set.of("READY", "PAUSED").contains(evidence.afterState())
                    && desired.equals("{\"desiredState\":\"" + evidence.afterState() + "\"}")
                    && ("simulation-application:" + execution.getExecutionKey())
                            .equals(evidence.evidenceRef());
        }
        return ("simulation-rollback:" + execution.getExecutionKey()).equals(evidence.evidenceRef())
                && evidence.beforeState() == null && evidence.afterState() == null;
    }

    public interface EvidencePort {
        Evidence read(String executionId, String executionKey, String targetId);
    }

    public record Evidence(String status, String executionId, String executionKey, String targetId,
            String currentState, String beforeState, String afterState, String evidenceRef) {}
}
