package com.nageoffer.ai.ragent.incident.action.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.contract.ActionBindingV1;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionOutboxWriter;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Append-only authenticated-result processing; conflicting evidence never overwrites a terminal fact. */
@Service
public final class ActionReceiptService {
    private static final ObjectMapper JSON = strictJson();
    private final ActionMapper actions;
    private final ExecutionMapper executions;
    private final ReceiptMapper receipts;
    private final ActionTransactionGuard guard;
    private final ActionTimelineWriter timeline;
    private final ActionOutboxWriter outbox;
    private final AlertMapper alerts;
    private final ActionServiceAuthentication authentication;

    public ActionReceiptService(ActionMapper actions, ExecutionMapper executions, ReceiptMapper receipts,
            ActionTransactionGuard guard, ActionTimelineWriter timeline, ActionOutboxWriter outbox,
            AlertMapper alerts, ActionServiceAuthentication authentication) {
        this.actions = actions;
        this.executions = executions;
        this.receipts = receipts;
        this.guard = guard;
        this.timeline = timeline;
        this.outbox = outbox;
        this.alerts = alerts;
        this.authentication = authentication;
    }

    public Acceptance accept(Submission input, String authorization) {
        if (!authentication.isAuthenticated(authorization)) {
            throw new ActionTicketConsumptionService.UnauthorizedException();
        }
        return process(input);
    }

    /** In-process second path for the trusted dispatch worker; never exposed as an HTTP route. */
    public Acceptance acceptFromWorker(Submission input) {
        return process(input);
    }

    private Acceptance process(Submission input) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.receipt.accept", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        String summary = requireValid(input);
        String digest = digest(input, summary);
        var candidate = actions.findById(input.actionId());
        if (candidate == null || !candidate.getIncidentId().equals(input.incidentId())) {
            throw new IllegalArgumentException("unbound Action receipt");
        }
        String[] committedReceipt = {null};
        var result = guard.inIncident(candidate.getIncidentId(), locked -> {
            var action = actions.lockById(input.actionId());
            var execution = executions.findByActionId(input.actionId());
            if (action == null || execution == null || execution.getExecutionStartedAt() == null
                    || !execution.getId().equals(input.executionId())
                    || !execution.getExecutionKey().equals(input.executionKey())
                    || !execution.getDispatchRequestId().equals(input.requestId())
                    || !execution.getToolName().equals(input.toolName())
                    || !execution.getToolVersion().equals(input.toolVersion())
                    || !execution.getTargetType().equals(input.targetType())
                    || !execution.getTargetId().equals(input.targetId())
                    || !ActionBindingV1.canonicalParameters(action.getParametersJson()).equals(
                            ActionBindingV1.canonicalParameters("{\"desiredState\":\""
                                    + input.desiredState() + "\"}"))
                    || !action.getBindingDigest().equals(execution.getBindingDigest())
                    || !action.getBindingDigest().equals(ActionBindingV1.bindingDigest(action.getId(),
                            action.getIncidentId(), action.getToolName(), action.getToolVersion(),
                            action.getTargetType(), action.getTargetId(), action.getParametersJson(),
                            action.getExecutionKey()))) {
                throw new IllegalArgumentException("unbound Action receipt");
            }
            com.nageoffer.ai.ragent.incident.observability.IncidentObservation.action(action);
            observation.id("executionId", execution.getId()).id("dispatchRequestId", execution.getDispatchRequestId());
            var previous = receipts.findById(input.receiptId());
            if (previous != null) {
                if (previous.getExecutionId().equals(execution.getId())
                        && previous.getReceiptDigest().equals(digest)) {
                    committedReceipt[0] = previous.getId();
                    return new Acceptance(previous.getDisposition(), action.getState(), false);
                }
                conflictFact(locked, action, execution, input.receiptId(), digest);
                return new Acceptance("CONFLICT", action.getState(), true);
            }
            String targetState = stateFor(input.resultStatus());
            boolean applied = "EXECUTING".equals(action.getState())
                    || ("UNKNOWN".equals(action.getState()) && !"UNKNOWN".equals(targetState));
            boolean consistent = !applied && targetState.equals(action.getState());
            String disposition = applied ? "APPLIED" : consistent ? "CONSISTENT" : "CONFLICT";
            OffsetDateTime now = locked.now();
            var receipt = new ReceiptMapper.ReceiptRow();
            receipt.setId(input.receiptId());
            receipt.setExecutionId(execution.getId());
            receipt.setActionId(action.getId());
            receipt.setRequestId(input.requestId());
            receipt.setReceiptDigest(digest);
            receipt.setResultStatus(input.resultStatus());
            receipt.setResultCode(input.resultCode());
            receipt.setToolName(input.toolName());
            receipt.setToolVersion(input.toolVersion());
            receipt.setTargetType(input.targetType());
            receipt.setTargetId(input.targetId());
            receipt.setExecutionKey(input.executionKey());
            receipt.setOccurredAt(input.occurredAt());
            receipt.setAcceptedAt(now);
            receipt.setSummaryJson(summary);
            receipt.setDisposition(disposition);
            receipts.insert(receipt);
            committedReceipt[0] = receipt.getId();
            if (applied) {
                if (actions.transition(action.getId(), action.getState(), action.getVersion(),
                        targetState, null, input.resultCode()) != 1
                        || executions.setCurrentReceipt(execution.getId(), execution.getCurrentReceiptId(),
                                receipt.getId()) != 1) throw new IllegalStateException("receipt state conflict");
                var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
                if (anchor == null) throw new IllegalStateException("Incident anchor missing");
                var fact = timeline.receiptAccepted(action.getIncidentId(), locked.reserveTimeline(1),
                        execution.getId(), receipt.getId(), input.resultStatus(), input.resultCode(),
                        anchor.getCorrelationId(), now);
                outbox.receiptAccepted(fact, actions.findById(action.getId()), receipt,
                        locked.row().getVersion(), anchor.getCorrelationId(), now);
            } else if (!consistent) {
                conflictFact(locked, action, execution, input.receiptId(), digest);
            }
            return new Acceptance(disposition, applied ? targetState : action.getState(),
                    !applied && !consistent);
        });
        observation.id("receiptId", committedReceipt[0]).outcome(result.disposition());
        if (result.conflict()) throw new ConflictException();
        return result;

        }
    }

    private void conflictFact(ActionTransactionGuard.LockedIncident locked, ActionMapper.ActionRow action,
            ExecutionMapper.ExecutionRow execution, String receiptId, String digest) {
        var anchor = alerts.findById(locked.row().getEpisodeAnchorAlertId());
        if (anchor == null) throw new IllegalStateException("Incident anchor missing");
        timeline.receiptConflict(action.getIncidentId(), locked.reserveTimeline(1), execution.getId(),
                receiptId, digest, anchor.getCorrelationId(), locked.now());
    }

    static String validatedSummary(String resultStatus, String summaryJson) {
        return requireValid(new Submission(1,"1",null,null,null,null,null,null,null,null,null,
                resultStatus,"QUERY",OffsetDateTime.now(),summaryJson));
    }

    private static String requireValid(Submission input) {
        if (input == null || input.schemaVersion() != 1
                || input.receiptId() == null || !input.receiptId().matches("[0-9]{1,20}")
                || input.resultStatus() == null
                || !Set.of("SUCCESS", "FAILED", "REJECTED", "UNKNOWN").contains(input.resultStatus())
                || input.resultCode() == null || !input.resultCode().matches("[A-Z][A-Z0-9_]{0,63}")
                || input.occurredAt() == null || input.summaryJson() == null
                || input.summaryJson().length() > 1024) {
            throw new IllegalArgumentException("invalid receipt");
        }
        try {
            var summary = JSON.readTree(input.summaryJson());
            if (summary == null || !summary.isObject() || !summary.has("desiredState")
                    || !summary.get("desiredState").isTextual()
                    || !Set.of("READY", "PAUSED").contains(summary.get("desiredState").textValue())
                    || !summary.has("applied") || summary.size() > 3) {
                throw new IllegalArgumentException("invalid receipt summary");
            }
            var names = summary.fieldNames();
            while (names.hasNext()) {
                if (!Set.of("desiredState", "applied", "proofCode").contains(names.next())) {
                    throw new IllegalArgumentException("invalid receipt summary");
                }
            }
            var applied = summary.get("applied");
            var proof = summary.get("proofCode");
            boolean success = "SUCCESS".equals(input.resultStatus());
            if ((success && (!applied.isBoolean() || !applied.booleanValue()))
                    || ("UNKNOWN".equals(input.resultStatus()) && !applied.isNull())
                    || (!success && !"UNKNOWN".equals(input.resultStatus())
                            && (!applied.isBoolean() || applied.booleanValue()))) {
                throw new IllegalArgumentException("invalid receipt evidence");
            }
            if ("FAILED".equals(input.resultStatus())
                    && (proof == null || !proof.isTextual()
                            || !Set.of("NOT_STARTED", "ROLLED_BACK").contains(proof.textValue()))) {
                throw new IllegalArgumentException("missing failure proof");
            }
            if ("REJECTED".equals(input.resultStatus())
                    && (proof == null || !proof.isTextual()
                            || !"PRE_EFFECT_REJECTION".equals(proof.textValue()))) {
                throw new IllegalArgumentException("missing rejection proof");
            }
            if (proof != null && (!proof.isTextual() || proof.textValue().length() > 64)) {
                throw new IllegalArgumentException("invalid proof");
            }
            return JSON.writeValueAsString(summary);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid receipt summary");
        }
    }

    private static String stateFor(String resultStatus) {
        return switch (resultStatus) {
            case "SUCCESS" -> "SUCCEEDED";
            case "FAILED" -> "FAILED";
            case "REJECTED" -> "EXECUTION_REJECTED";
            case "UNKNOWN" -> "UNKNOWN";
            default -> throw new IllegalArgumentException("invalid resultStatus");
        };
    }

    private static String digest(Submission input, String summary) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            for (String field : new String[] {Integer.toString(input.schemaVersion()), input.receiptId(),
                    input.actionId(), input.incidentId(),
                    input.executionId(), input.executionKey(), input.requestId(), input.toolName(),
                    input.toolVersion(), input.targetType(), input.targetId(), input.resultStatus(),
                    input.resultCode(), input.occurredAt().toInstant().toString(), summary}) {
                if (field == null || field.indexOf('\0') >= 0) throw new IllegalArgumentException("invalid receipt field");
                md.update(field.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ObjectMapper strictJson() {
        var mapper = new ObjectMapper();
        mapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        return mapper;
    }

    public record Submission(int schemaVersion, String receiptId, String actionId, String incidentId, String executionId,
            String executionKey, String requestId, String toolName, String toolVersion, String targetType,
            String targetId, String resultStatus, String resultCode, OffsetDateTime occurredAt,
            String summaryJson) {
        public String desiredState() {
            try {
                return JSON.readTree(summaryJson).get("desiredState").textValue();
            } catch (Exception e) {
                throw new IllegalArgumentException("invalid receipt summary");
            }
        }
    }
    public record Acceptance(String disposition, String actionState, boolean conflict) {}
    public static final class ConflictException extends RuntimeException {}
}
