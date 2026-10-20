package com.nageoffer.ai.ragent.incident.action.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.incident.action.config.ActionProperties;
import com.nageoffer.ai.ragent.incident.action.contract.ActionBindingV1;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Creates a proposal and its approval under the Incident lock; no tool is invoked here. */
@Service
public final class ActionProposalService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ActionMapper actions;
    private final ApprovalMapper approvals;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final AlertMapper alerts;
    private final ActionProperties properties;

    public ActionProposalService(ActionMapper actions, ApprovalMapper approvals, ActionTransactionGuard guard,
            ActionTimelineWriter timeline, AlertMapper alerts, ActionProperties properties) {
        this.actions = actions;
        this.approvals = approvals;
        this.guard = guard;
        this.timeline = timeline;
        this.alerts = alerts;
        this.properties = properties;
    }

    public ProposalResult proposeUser(UserProposal request) {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
        String actorId = StpUtil.getLoginIdAsString();
        return propose(request, "USER", actorId);
    }

    private ProposalResult propose(UserProposal request, String sourceType, String actorId) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.action.propose", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        if (request == null || actorId == null || actorId.isBlank()) throw new IllegalArgumentException("invalid proposer");
        if (!properties.getAllowedSimulationTargets().contains(request.targetId())) {
            throw new IllegalArgumentException("unregistered simulation target");
        }
        String parameters = ActionBindingV1.canonicalParameters(request.parametersJson());
        String digest = ActionBindingV1.proposalDigest(request.incidentId(), sourceType,
                request.sourceRequestId(), request.toolName(), request.toolVersion(),
                ActionBindingV1.TARGET_TYPE, request.targetId(), parameters);
        var proposed = guard.inIncident(request.incidentId(), locked -> {
            if ("CLOSED".equals(locked.row().getLifecycleState())) throw new ConflictException();
            var existing = actions.findBySource(request.incidentId(), sourceType, request.sourceRequestId());
            if (existing != null) {
                if (!existing.getProposalDigest().equals(digest)
                        || !Objects.equals(existing.getOriginalActionId(), request.originalActionId())) {
                    throw new SourceConflictException();
                }
                com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(existing);
                observation.outcome("REUSED");
                return new ProposalResult(existing, true);
            }
            if (request.originalActionId() != null) {
                var original = actions.findById(request.originalActionId());
                if (original == null || !request.incidentId().equals(original.getIncidentId())) {
                    throw new IllegalArgumentException("original Action not in Incident");
                }
            }
            String actionId = IdUtil.getSnowflakeNextIdStr();
            byte[] random = new byte[32];
            RANDOM.nextBytes(random);
            String executionKey = HexFormat.of().formatHex(random);
            var due = locked.now().plus(properties.getApprovalTtl());
            var next = new ActionMapper.NewAction();
            next.setId(actionId);
            next.setIncidentId(request.incidentId());
            next.setSourceType(sourceType);
            next.setSourceRequestId(request.sourceRequestId());
            next.setProposedByType(sourceType);
            next.setProposedById(actorId);
            next.setOriginalActionId(request.originalActionId());
            next.setToolName(request.toolName());
            next.setToolVersion(request.toolVersion());
            next.setTargetType(ActionBindingV1.TARGET_TYPE);
            next.setTargetId(request.targetId());
            next.setParametersJson(parameters);
            next.setProposalDigest(digest);
            next.setBindingVersion(ActionBindingV1.VERSION);
            next.setBindingDigest(ActionBindingV1.bindingDigest(actionId, request.incidentId(),
                    request.toolName(), request.toolVersion(), ActionBindingV1.TARGET_TYPE,
                    request.targetId(), parameters, executionKey));
            next.setExecutionKey(executionKey);
            next.setApprovalDueAt(due);
            actions.insert(next);
            approvals.insertPending(IdUtil.getSnowflakeNextIdStr(), actionId, next.getBindingDigest(), due);
            var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
            if (anchor == null) throw new IllegalStateException("Incident anchor missing");
            timeline.proposed(request.incidentId(), locked.reserveTimeline(1), actionId, sourceType,
                    actorId, anchor.getCorrelationId(), locked.now());
            return new ProposalResult(actions.findById(actionId), false);
        });
        com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(proposed.action());
        observation.id("sourceRequestId", request.sourceRequestId()).outcome(proposed.repeated() ? "REUSED" : "PENDING_APPROVAL");
        return proposed;
        }
    }

    public ActionMapper.ActionRow find(String actionId) {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
        return actions.findById(actionId);
    }

    public record UserProposal(String incidentId, String sourceRequestId, String originalActionId,
            String toolName, String toolVersion, String targetId, String parametersJson) {}
    public record ProposalResult(ActionMapper.ActionRow action, boolean repeated) {}
    public static final class SourceConflictException extends RuntimeException {}
    public static final class ConflictException extends RuntimeException {}
}
