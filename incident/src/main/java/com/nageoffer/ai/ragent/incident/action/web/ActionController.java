package com.nageoffer.ai.ragent.incident.action.web;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionProposalService;
import com.nageoffer.ai.ragent.incident.action.service.ActionManualConclusionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReconciliationService;
import com.nageoffer.ai.ragent.incident.action.service.ActionQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Personnel API. Source and approver identity are always taken from the session. */
@RestController
@RequestMapping("/incident/v1")
public final class ActionController {
    private final ActionProposalService proposals;
    private final ActionDecisionService decisions;
    private final ApprovalMapper approvals;
    private ActionManualConclusionService manual;
    private ActionReconciliationService reconciliation;
    private ActionQueryService queries;
    private final ObjectMapper json = new ObjectMapper();

    public ActionController(ActionProposalService proposals, ActionDecisionService decisions,
            ApprovalMapper approvals) {
        this.proposals = proposals;
        this.decisions = decisions;
        this.approvals = approvals;
        json.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        json.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Autowired(required = false)
    public void setRecoveryServices(ActionManualConclusionService manual,
            ActionReconciliationService reconciliation) {
        this.manual = manual;
        this.reconciliation = reconciliation;
    }

    @Autowired(required = false)
    public void setQueryService(ActionQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/incidents/{incidentId}/actions")
    public ResponseEntity<Result<List<Map<String, Object>>>> list(@PathVariable String incidentId,
            HttpServletRequest request) {
        staff();
        id(incidentId);
        if (queries == null) throw new IllegalStateException("Action query unavailable");
        return ResponseEntity.ok(new Result<List<Map<String, Object>>>().setCode("SUCCESS")
                .setMessage("OK").setRequestId(com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId(request))
                .setData(queries.list(incidentId)));
    }

    @PostMapping("/actions/{actionId}/reconcile")
    public ResponseEntity<Result<Map<String, Object>>> reconcile(@PathVariable String actionId,
            @RequestBody String body, HttpServletRequest request) {
        staff();
        id(actionId);
        if (reconciliation == null) throw new IllegalStateException("reconciliation unavailable");
        var root = parse(body, Set.of("requestId", "expectedVersion"));
        string(root, "requestId");
        long version = version(root);
        reconciliation.reconcile(actionId, version);
        return ResponseEntity.accepted().body(success(view(proposals.find(actionId)), request));
    }

    @PostMapping("/actions/{actionId}/manual-conclusion")
    public ResponseEntity<Result<Map<String, Object>>> manualConclusion(@PathVariable String actionId,
            @RequestBody String body, HttpServletRequest request) {
        staff();
        id(actionId);
        if (manual == null) throw new IllegalStateException("manual conclusion unavailable");
        var root = parse(body, Set.of("requestId", "expectedVersion", "outcome", "reason", "evidenceRef"));
        manual.conclude(actionId, version(root), string(root, "outcome"), string(root, "reason"),
                string(root, "evidenceRef"), string(root, "requestId"));
        return ResponseEntity.ok(success(view(proposals.find(actionId)), request));
    }

    @PostMapping("/incidents/{incidentId}/actions")
    public ResponseEntity<Result<Map<String, Object>>> propose(@PathVariable String incidentId,
            @RequestBody String body, HttpServletRequest request) {
        staff();
        id(incidentId);
        var root = parse(body, Set.of("sourceRequestId", "originalActionId", "toolName",
                "toolVersion", "targetId", "parameters"));
        var proposal = new ActionProposalService.UserProposal(incidentId,
                string(root, "sourceRequestId"), optionalString(root, "originalActionId"),
                string(root, "toolName"), string(root, "toolVersion"), string(root, "targetId"),
                parameters(root));
        var result = proposals.proposeUser(proposal);
        return ResponseEntity.status(result.repeated() ? 200 : 201)
                .body(success(view(result.action()), request));
    }

    @GetMapping("/actions/{actionId}")
    public ResponseEntity<Result<Map<String, Object>>> get(@PathVariable String actionId,
            HttpServletRequest request) {
        staff();
        id(actionId);
        if (queries != null) return ResponseEntity.ok(success(queries.get(actionId), request));
        var action = proposals.find(actionId);
        if (action == null) throw new ActionDecisionService.NotFoundException();
        return ResponseEntity.ok(success(view(action), request));
    }

    @PostMapping("/actions/{actionId}/decision")
    public ResponseEntity<Result<Map<String, Object>>> decide(@PathVariable String actionId,
            @RequestBody String body, HttpServletRequest request) {
        staff();
        id(actionId);
        var root = parse(body, Set.of("decision", "expectedVersion", "reason"));
        if (!root.has("expectedVersion") || !root.get("expectedVersion").canConvertToLong()
                || root.get("expectedVersion").longValue() < 0) throw new IllegalArgumentException();
        var result = decisions.decide(actionId, root.get("expectedVersion").longValue(),
                string(root, "decision"), optionalString(root, "reason"), com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId(request));
        return ResponseEntity.ok(success(view(result), request));
    }

    private JsonNode parse(String body, Set<String> allowed) {
        final JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new InvalidJsonException();
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException();
        root.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException();
        });
        return root;
    }

    private String parameters(JsonNode root) {
        if (!root.has("parameters") || !root.get("parameters").isObject()) throw new IllegalArgumentException();
        return root.get("parameters").toString();
    }

    private static String string(JsonNode root, String key) {
        if (!root.hasNonNull(key) || !root.get(key).isTextual()) throw new IllegalArgumentException();
        return root.get(key).textValue();
    }

    private static String optionalString(JsonNode root, String key) {
        if (!root.has(key)) return null;
        return string(root, key);
    }

    private static void id(String id) {
        if (!id.matches("[0-9]{1,20}")) throw new IllegalArgumentException();
    }

    private static long version(JsonNode root) {
        if (!root.has("expectedVersion") || !root.get("expectedVersion").canConvertToLong()
                || root.get("expectedVersion").longValue() < 0) throw new IllegalArgumentException();
        return root.get("expectedVersion").longValue();
    }

    private static void staff() {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
    }

    private Map<String, Object> view(ActionMapper.ActionRow row) {
        var data = new LinkedHashMap<String, Object>();
        data.put("actionId", row.getId());
        data.put("incidentId", row.getIncidentId());
        data.put("state", row.getState());
        data.put("version", row.getVersion());
        data.put("toolName", row.getToolName());
        data.put("toolVersion", row.getToolVersion());
        data.put("targetType", row.getTargetType());
        data.put("targetId", row.getTargetId());
        data.put("bindingDigest", row.getBindingDigest());
        if (row.getOriginalActionId() != null) data.put("originalActionId", row.getOriginalActionId());
        if (row.getLastReasonCode() != null) data.put("lastReasonCode", row.getLastReasonCode());
        var approval = approvals.findByActionId(row.getId());
        if (approval == null) throw new IllegalStateException("Action approval missing");
        var approvalView = new LinkedHashMap<String, Object>();
        approvalView.put("status", approval.getStatus());
        approvalView.put("approvalDueAt", approval.getApprovalDueAt().toString());
        if (approval.getAuthorizationDueAt() != null) {
            approvalView.put("authorizationDueAt", approval.getAuthorizationDueAt().toString());
        }
        if (approval.getDecidedAt() != null) approvalView.put("decidedAt", approval.getDecidedAt().toString());
        if (approval.getDeciderId() != null) approvalView.put("deciderId", approval.getDeciderId());
        if (approval.getReasonCode() != null) approvalView.put("reasonCode", approval.getReasonCode());
        data.put("approval", approvalView);
        data.put("nextStep", switch (row.getState()) {
            case "PENDING_APPROVAL" -> "AWAIT_APPROVAL";
            case "APPROVED" -> "AWAIT_DISPATCH";
            case "DISPATCHING", "EXECUTING" -> "AWAIT_RECEIPT";
            case "UNKNOWN" -> "RECONCILE";
            default -> "NONE";
        });
        return data;
    }

    private static Result<Map<String, Object>> success(Map<String, Object> data, HttpServletRequest request) {
        return new Result<Map<String, Object>>().setCode("SUCCESS").setMessage("OK")
                .setRequestId(com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId(request)).setData(data);
    }

    public static final class InvalidJsonException extends RuntimeException {}
}
