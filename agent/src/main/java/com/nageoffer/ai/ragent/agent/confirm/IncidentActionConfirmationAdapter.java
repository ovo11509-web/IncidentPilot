package com.nageoffer.ai.ragent.agent.confirm;

import java.util.Map;
import java.util.Set;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import org.springframework.stereotype.Component;

/** An Incident card is a read-only projection of an authoritative Action, never execution authority. */
@Component
public final class IncidentActionConfirmationAdapter {
    private static final Set<String> STATES = Set.of("PENDING_APPROVAL", "APPROVED",
            "APPROVAL_REJECTED", "DISPATCHING", "EXECUTING", "SUCCEEDED", "FAILED",
            "EXECUTION_REJECTED", "EXPIRED", "UNKNOWN");

    private final ActionMapper actions;

    public IncidentActionConfirmationAdapter(ActionMapper actions) {
        this.actions = actions;
    }

    public Card load(String actionId) {
        if (actionId == null || !actionId.matches("[0-9]{1,20}")) throw new IllegalArgumentException("actionId");
        var snapshot = actions.findById(actionId);
        if (snapshot == null || !actionId.equals(snapshot.getId()) || !STATES.contains(snapshot.getState())) {
            throw new IllegalStateException("Action status unavailable");
        }
        return new Card(actionId, snapshot.getState());
    }

    public record Card(String actionId, String state) {
        public Map<String, String> safeFields() {
            return Map.of("actionId", actionId, "state", state);
        }
    }
}
