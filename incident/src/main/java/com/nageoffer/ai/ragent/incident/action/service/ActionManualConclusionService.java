package com.nageoffer.ai.ragent.incident.action.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Human evidence may resolve only UNKNOWN and never rewrite a deterministic outcome. */
@Service
public final class ActionManualConclusionService {
    public static final String PERMISSION = "INCIDENT_ACTION_RESOLVE_UNKNOWN";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ActionMapper actions;
    private final ExecutionMapper executions;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final TimelineMapper facts;
    private final AlertMapper alerts;

    public ActionManualConclusionService(ActionMapper actions, ExecutionMapper executions,
            ActionTransactionGuard guard, ActionTimelineWriter timeline, TimelineMapper facts,
            AlertMapper alerts) {
        this.actions = actions;
        this.executions = executions;
        this.guard = guard;
        this.timeline = timeline;
        this.facts = facts;
        this.alerts = alerts;
    }

    public String conclude(String actionId, long expectedVersion, String outcome, String reason,
            String evidenceRef, String requestId) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.action.manual", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        // P0 maps this dedicated business permission only to the current admin role.
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
        String actor = StpUtil.getLoginIdAsString();
        if (actionId == null || !actionId.matches("[0-9]{1,20}") || expectedVersion < 0
                || outcome == null || !Set.of("SUCCEEDED", "FAILED").contains(outcome)
                || requestId == null || !requestId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || reason == null || reason.isBlank() || reason.length() > 512
                || reason.matches("(?is).*\\bBearer\\s+\\S+.*")
                || reason.matches("(?is).*[0-9a-f]{64}.*")
                || reason.chars().anyMatch(Character::isISOControl)
                || evidenceRef == null || evidenceRef.length() > 256
                || !evidenceRef.matches("(?:case|timeline|simulation-application|simulation-rollback):"
                        + "[A-Za-z0-9][A-Za-z0-9._:-]{0,220}")) {
            throw new IllegalArgumentException("invalid manual evidence");
        }
        var candidate = actions.findById(actionId);
        if (candidate == null) throw new ActionDecisionService.NotFoundException();
        com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(candidate);
        return guard.inIncident(candidate.getIncidentId(), locked -> {
            var action = actions.lockById(actionId);
            var execution = executions.findByActionId(actionId);
            if (action == null || execution == null || execution.getExecutionStartedAt() == null)
                throw new IllegalStateException("unstarted Action cannot be concluded");
            String key = "action:" + actionId + ":manual:" + requestId;
            var existing = facts.findByBusinessKey(key);
            if (existing != null) {
                try {
                    var prior = JSON.readTree(existing.getSummaryJson());
                    if (actor.equals(existing.getActorId()) && outcome.equals(prior.path("outcome").asText())
                            && reason.equals(prior.path("reason").asText())
                            && evidenceRef.equals(prior.path("evidenceRef").asText())) {
                        return action.getState();
                    }
                } catch (Exception invalid) {
                    throw new IllegalStateException("manual evidence corrupt");
                }
                throw new IllegalStateException("manual request identity conflict");
            }
            if (!"UNKNOWN".equals(action.getState()) || action.getVersion() != expectedVersion)
                throw new IllegalStateException("manual conclusion conflict");
            if (actions.transition(actionId, "UNKNOWN", expectedVersion, outcome,
                    null, "MANUAL_EVIDENCE") != 1) throw new IllegalStateException("manual state conflict");
            var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
            if (anchor == null) throw new IllegalStateException("Incident anchor missing");
            timeline.manualConclusion(action.getIncidentId(), locked.reserveTimeline(1), actionId,
                    requestId, outcome, reason, evidenceRef, actor, anchor.getCorrelationId(), locked.now());
            return outcome;
        });

        }
    }
}
