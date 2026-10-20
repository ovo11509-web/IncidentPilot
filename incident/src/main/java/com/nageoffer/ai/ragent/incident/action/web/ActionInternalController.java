package com.nageoffer.ai.ragent.incident.action.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MCP callback route. Only the bound service credential can reach the consumption transaction. */
@RestController
@RequestMapping("/incident/internal/v1")
public final class ActionInternalController {
    private final ActionTicketConsumptionService consumption;
    private final ActionReceiptService receipts;
    private final ObjectMapper json = new ObjectMapper();

    public ActionInternalController(ActionTicketConsumptionService consumption, ActionReceiptService receipts) {
        this.consumption = consumption;
        this.receipts = receipts;
        json.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        json.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @PostMapping("/action-receipts")
    public ResponseEntity<Result<Map<String, Object>>> receipt(@RequestBody String body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!consumption.isAuthenticated(authorization)) {
            throw new ActionTicketConsumptionService.UnauthorizedException();
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new InvalidJsonException();
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("invalid receipt");
        var allowed = Set.of("schemaVersion", "receiptId", "actionId", "incidentId", "executionId",
                "executionKey", "requestId", "toolName", "toolVersion", "target", "resultStatus",
                "resultCode", "occurredAt", "summary");
        root.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("unknown receipt field");
        });
        if (root.size() != allowed.size() || !root.path("schemaVersion").isInt()
                || !root.path("target").isObject() || root.path("target").size() != 2
                || !root.path("summary").isObject()) throw new IllegalArgumentException("invalid receipt");
        var target = root.get("target");
        if (!target.has("type") || !target.has("id")) throw new IllegalArgumentException("invalid target");
        OffsetDateTime occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(string(root, "occurredAt"));
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid occurredAt");
        }
        var submission = new ActionReceiptService.Submission(root.get("schemaVersion").intValue(),
                string(root, "receiptId"), string(root, "actionId"), string(root, "incidentId"),
                string(root, "executionId"), string(root, "executionKey"), string(root, "requestId"),
                string(root, "toolName"), string(root, "toolVersion"), string(target, "type"),
                string(target, "id"), string(root, "resultStatus"), string(root, "resultCode"),
                occurredAt, root.get("summary").toString());
        var accepted = receipts.accept(submission, authorization);
        return ResponseEntity.ok(new Result<Map<String, Object>>().setCode("SUCCESS")
                .setMessage("OK").setRequestId(com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId())
                .setData(Map.of("receiptId", submission.receiptId(),
                        "disposition", accepted.disposition(), "actionState", accepted.actionState())));
    }

    @PostMapping("/action-tickets/consume")
    public ResponseEntity<Result<Map<String, Object>>> consume(@RequestBody String body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        // Reject before parsing untrusted payloads or looking up self-reported Action identifiers.
        if (!consumption.isAuthenticated(authorization)) {
            throw new ActionTicketConsumptionService.UnauthorizedException();
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new InvalidJsonException();
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("invalid request");
        var allowed = Set.of("ticket", "actionId", "incidentId", "executionId", "executionKey",
                "requestId", "bindingVersion", "toolName", "toolVersion", "targetId", "parameters");
        root.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw new IllegalArgumentException("unknown field");
        });
        if (!root.has("parameters") || !root.get("parameters").isObject()) {
            throw new IllegalArgumentException("invalid parameters");
        }
        var input = new ActionTicketConsumptionService.ConsumeRequest(string(root, "ticket"),
                string(root, "actionId"), string(root, "incidentId"), string(root, "executionId"),
                string(root, "executionKey"), string(root, "requestId"), string(root, "bindingVersion"),
                string(root, "toolName"), string(root, "toolVersion"), "SIM_SERVICE",
                string(root, "targetId"), root.get("parameters").toString());
        var result = consumption.consume(input, authorization);
        var safe = new LinkedHashMap<String, Object>();
        safe.put("decision", result.decision());
        safe.put("actionId", input.actionId());
        safe.put("executionId", input.executionId());
        safe.put("receiptDueAt", result.receiptDueAt().toString());
        safe.put("maxExecutionDurationMs", result.maxExecutionDurationMs());
        return ResponseEntity.ok(new Result<Map<String, Object>>().setCode("SUCCESS")
                .setMessage("OK").setRequestId(com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId()).setData(safe));
    }

    private static String string(JsonNode root, String name) {
        if (!root.hasNonNull(name) || !root.get(name).isTextual()) throw new IllegalArgumentException("invalid " + name);
        return root.get(name).textValue();
    }

    public static final class InvalidJsonException extends RuntimeException {}
}
