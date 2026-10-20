package com.nageoffer.ai.ragent.incident.action.service;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Builds a personnel-safe view exclusively from committed Incident facts. */
@Service
public final class ActionQueryService {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private final ActionMapper actions;
    private final ApprovalMapper approvals;
    private final ExecutionMapper executions;
    private final TicketMapper tickets;
    private final ReceiptMapper receipts;
    private final IncidentMapper incidents;
    private final TimelineMapper timeline;
    private final TransactionTemplate reads;
    private com.nageoffer.ai.ragent.incident.diagnosis.dao.DiagnosisActionLinkMapper diagnosisLinks;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setDiagnosisLinks(com.nageoffer.ai.ragent.incident.diagnosis.dao.DiagnosisActionLinkMapper links){this.diagnosisLinks=links;}

    public ActionQueryService(ActionMapper actions, ApprovalMapper approvals,
            ExecutionMapper executions, TicketMapper tickets, ReceiptMapper receipts,
            IncidentMapper incidents, TimelineMapper timeline, PlatformTransactionManager manager) {
        this.actions = actions;
        this.approvals = approvals;
        this.executions = executions;
        this.tickets = tickets;
        this.receipts = receipts;
        this.incidents = incidents;
        this.timeline = timeline;
        this.reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public Map<String, Object> get(String actionId) {
        staff();
        return reads.execute(status -> {
            var action = actions.findById(actionId);
            if (action == null || incidents.findById(action.getIncidentId()) == null)
                throw new ActionDecisionService.NotFoundException();
            var execution = executions.findByActionId(actionId);
            var facts = timeline.findActionFacts(action.getIncidentId(), actionId,
                    execution == null ? null : execution.getId());
            return view(action, execution, facts);
        });
    }

    public List<Map<String, Object>> list(String incidentId) {
        staff();
        return reads.execute(status -> {
            if (incidents.findById(incidentId) == null) throw new ActionDecisionService.NotFoundException();
            var rows = actions.findByIncidentId(incidentId);
            if (rows.isEmpty()) return List.of();
            var byEntity = timeline.findActionFacts(incidentId, null, null).stream().collect(
                    java.util.stream.Collectors.groupingBy(f -> f.getRelatedEntityType() + ":" + f.getRelatedEntityId()));
            return rows.stream().map(action -> {
                var execution = executions.findByActionId(action.getId());
                var facts = new ArrayList<>(byEntity.getOrDefault("ACTION:" + action.getId(), List.of()));
                if (execution != null) facts.addAll(byEntity.getOrDefault("EXECUTION:" + execution.getId(), List.of()));
                facts.sort(java.util.Comparator.comparingLong(TimelineMapper.Fact::getSequenceNo));
                return view(action, execution, facts);
            }).toList();
        });
    }

    private Map<String, Object> view(ActionMapper.ActionRow action, ExecutionMapper.ExecutionRow execution,
            List<TimelineMapper.Fact> relatedFacts) {
        var result = new LinkedHashMap<String, Object>();
        result.put("actionId", action.getId());
        result.put("incidentId", action.getIncidentId());
        result.put("state", action.getState());
        result.put("version", action.getVersion());
        result.put("toolName", action.getToolName());
        result.put("toolVersion", action.getToolVersion());
        result.put("targetType", action.getTargetType());
        result.put("targetId", action.getTargetId());
        result.put("bindingDigest", action.getBindingDigest());
        result.put("parametersAvailable", false);
        try {
            String canonical=com.nageoffer.ai.ragent.incident.action.contract.ActionBindingV1.canonicalParameters(action.getParametersJson());
            result.put("parameters", Map.of("desiredState",JSON.readTree(canonical).path("desiredState").asText()));
            result.put("parametersAvailable", true);
        } catch(Exception ignored) { /* Invalid persisted data is readable, never approvable. */ }
        if(diagnosisLinks!=null) {
            var link=diagnosisLinks.find(action.getId());
            if(link!=null)result.put("diagnosisSource",Map.of("diagnosisId",link.getDiagnosisId(),"recommendationId",link.getRecommendationId(),
                "diagnosisDigest",link.getDiagnosisDigest(),"recommendationDigest",link.getRecommendationDigest(),"factsDigest",link.getFactsDigest()));
        }
        if (action.getOriginalActionId() != null) result.put("originalActionId", action.getOriginalActionId());
        if (action.getLastReasonCode() != null) result.put("lastReasonCode", action.getLastReasonCode());
        var approval = approvals.findByActionId(action.getId());
        if (approval == null) throw new IllegalStateException("Action approval missing");
        var approvalView = new LinkedHashMap<String, Object>();
        approvalView.put("status", approval.getStatus());
        putTime(approvalView, "approvalDueAt", approval.getApprovalDueAt());
        putTime(approvalView, "authorizationDueAt", approval.getAuthorizationDueAt());
        putTime(approvalView, "decidedAt", approval.getDecidedAt());
        if (approval.getDeciderId() != null) approvalView.put("deciderId", approval.getDeciderId());
        if (approval.getReasonCode() != null) approvalView.put("reasonCode", approval.getReasonCode());
        result.put("approval", approvalView);
        if (execution != null) {
            var allTickets = tickets.findByExecutionId(execution.getId());
            var allReceipts = receipts.findByExecutionId(execution.getId());
            var executionView = new LinkedHashMap<String, Object>();
            executionView.put("executionId", execution.getId());
            executionView.put("executionKey", execution.getExecutionKey());
            if (!allTickets.isEmpty()) executionView.put("ticketStatus", allTickets.get(allTickets.size() - 1).getStatus());
            putTime(executionView, "executionStartedAt", execution.getExecutionStartedAt());
            putTime(executionView, "receiptDueAt", execution.getReceiptDueAt());
            if (execution.getCurrentReceiptId() != null) {
                var current = allReceipts.stream().filter(r -> execution.getCurrentReceiptId().equals(r.getId()))
                        .findFirst().orElse(null);
                if (current != null) {
                    executionView.put("resultStatus", current.getResultStatus());
                    executionView.put("resultCode", current.getResultCode());
                }
            }
            result.put("execution", executionView);
            result.put("tickets", allTickets.stream().map(ticket -> {
                var safe = new LinkedHashMap<String, Object>();
                safe.put("ticketId", ticket.getId());
                safe.put("status", ticket.getStatus());
                putTime(safe, "issuedAt", ticket.getIssuedAt());
                putTime(safe, "expiresAt", ticket.getExpiresAt());
                if (ticket.getInvalidationReason() != null)
                    safe.put("invalidationReason", ticket.getInvalidationReason());
                return safe;
            }).toList());
            result.put("receipts", allReceipts.stream().map(receipt -> {
                var safe = new LinkedHashMap<String, Object>();
                safe.put("receiptId", receipt.getId());
                safe.put("resultStatus", receipt.getResultStatus());
                safe.put("resultCode", receipt.getResultCode());
                safe.put("disposition", receipt.getDisposition());
                putTime(safe, "occurredAt", receipt.getOccurredAt());
                safe.put("summaryAvailable",false);
                try {
                    var summary=JSON.readTree(ActionReceiptService.validatedSummary(receipt.getResultStatus(),receipt.getSummaryJson()));
                    var proof=summary.get("proofCode");
                    if(proof!=null && !java.util.Set.of("NOT_STARTED","ROLLED_BACK","PRE_EFFECT_REJECTION").contains(proof.asText()))
                        throw new IllegalArgumentException("unknown proof");
                    var projected=new LinkedHashMap<String,Object>();
                    projected.put("desiredState",summary.path("desiredState").asText());
                    projected.put("applied",summary.path("applied").isNull()?null:summary.path("applied").booleanValue());
                    if(proof!=null)projected.put("proofCode",proof.asText());
                    safe.put("summary",projected);safe.put("summaryAvailable",true);
                } catch(Exception ignored) { /* Never expose malformed or unrecognized summary text. */ }
                putTime(safe, "acceptedAt", receipt.getAcceptedAt());
                return safe;
            }).toList());
        }
        var facts = new ArrayList<Map<String, Object>>();
        for (var fact : relatedFacts) {
            var safe = new LinkedHashMap<String, Object>();
            safe.put("sequenceNo", fact.getSequenceNo());
            safe.put("businessEventKey", fact.getBusinessEventKey());
            safe.put("eventType", fact.getEventType());
            safe.put("relatedEntityType", fact.getRelatedEntityType());
            safe.put("relatedEntityId", fact.getRelatedEntityId());
            facts.add(safe);
        }
        result.put("timeline", facts);
        result.put("nextStep", switch (action.getState()) {
            case "PENDING_APPROVAL" -> "AWAIT_APPROVAL";
            case "APPROVED" -> "AWAIT_DISPATCH";
            case "DISPATCHING", "EXECUTING" -> "AWAIT_RECEIPT";
            case "UNKNOWN" -> "RECONCILE";
            case "FAILED", "EXECUTION_REJECTED" -> "REVIEW_EVIDENCE";
            default -> "NONE";
        });
        return result;
    }

    private static void putTime(Map<String, Object> map, String key, OffsetDateTime time) {
        if (time != null) map.put(key, time.toString());
    }

    private static void staff() {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
    }
}
