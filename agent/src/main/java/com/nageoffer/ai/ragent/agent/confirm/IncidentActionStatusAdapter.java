package com.nageoffer.ai.ragent.agent.confirm;

import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Restores an Agent card from the committed Action row without dispatching any tool. */
@Component
public final class IncidentActionStatusAdapter {
    private final ActionMapper actions;

    public IncidentActionStatusAdapter(ActionMapper actions) {
        this.actions = actions;
    }

    public Map<String, String> load(String actionId) {
        if (actionId == null || !actionId.matches("[0-9]{1,20}"))
            throw new IllegalArgumentException("actionId");
        var action = actions.findById(actionId);
        if (action == null || !actionId.equals(action.getId()))
            throw new IllegalStateException("Action status unavailable");
        String nextStep = switch (action.getState()) {
            case "PENDING_APPROVAL" -> "AWAIT_APPROVAL";
            case "APPROVED" -> "AWAIT_DISPATCH";
            case "DISPATCHING", "EXECUTING" -> "AWAIT_RECEIPT";
            case "UNKNOWN" -> "RECONCILE";
            case "FAILED", "EXECUTION_REJECTED" -> "REVIEW_EVIDENCE";
            case "SUCCEEDED", "APPROVAL_REJECTED", "EXPIRED" -> "NONE";
            default -> throw new IllegalStateException("Action status unavailable");
        };
        return Map.of("actionId", actionId, "incidentId", action.getIncidentId(),
                "state", action.getState(), "nextStep", nextStep);
    }
}
