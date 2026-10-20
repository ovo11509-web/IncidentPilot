package com.nageoffer.ai.ragent.mcp.simulation;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.mcp.config.McpToolAnnotations;
import com.nageoffer.ai.ragent.mcp.executor.McpToolResults;
import com.nageoffer.ai.ragent.mcp.executor.McpToolSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/** Exactly one side-effect path: authenticated protected transport, online ticket, isolated DB. */
@Component
public final class ProtectedSimulationTool {
    private static final Set<String> META = Set.of("actionId", "incidentId", "executionId",
            "executionKey", "requestId", "ticket", "bindingVersion");
    private final SimulationToolExecutor executor;
    private final IncidentCallbackClient callbacks;
    private final SimulationTimeoutEvaluator timeout;

    public ProtectedSimulationTool(SimulationToolExecutor executor, IncidentCallbackClient callbacks) {
        this(executor, callbacks, new SimulationTimeoutEvaluator());
    }

    @Autowired
    public ProtectedSimulationTool(SimulationToolExecutor executor, IncidentCallbackClient callbacks,
            SimulationTimeoutEvaluator timeout) {
        this.executor = executor;
        this.callbacks = callbacks;
        this.timeout = timeout;
    }

    @Bean
    public McpServerFeatures.SyncToolSpecification protectedSimulationSpecification() {
        var input = McpToolSchema.object()
                .required(McpToolSchema.string("targetId", "已登记的隔离模拟服务 ID"))
                .required(McpToolSchema.string("desiredState", "READY 或 PAUSED")
                        .options(java.util.List.of("READY", "PAUSED")))
                .build();
        var tool = Tool.builder().name("simulation.set-state")
                .description("仅供已审批 Incident Worker 调用的隔离模拟状态切换")
                .inputSchema(input).annotations(McpToolAnnotations.WRITE).build();
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            Object captured = exchange.transportContext().get("incident.observation");
            if (captured instanceof McpObservation.Snapshot snapshot) {
                try (var context = snapshot.makeCurrent()) { return invoke(request); }
            }
            return invoke(request);
        });
    }

    CallToolResult invoke(CallToolRequest request) {
        if (request == null || !"simulation.set-state".equals(request.name())) {
            return McpToolResults.error("INVALID_PROTECTED_REQUEST");
        }
        Map<String, Object> args = request.arguments() == null
                ? Map.of() : request.arguments();
        Map<String, Object> meta = request.meta() == null ? Map.of() : request.meta();
        if (!args.keySet().equals(Set.of("targetId", "desiredState")) || !meta.keySet().equals(META)) {
            return McpToolResults.error("INVALID_PROTECTED_REQUEST");
        }
        String targetId = field(args, "targetId");
        String desiredState = field(args, "desiredState");
        if (targetId == null || !targetId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || desiredState == null || !Set.of("READY", "PAUSED").contains(desiredState)
                || !"action-binding-v1".equals(field(meta, "bindingVersion"))) {
            return McpToolResults.error("INVALID_PROTECTED_REQUEST");
        }
        for (String name : META) {
            var value = field(meta, name);
            if (value == null || value.isBlank() || value.length() > 256) {
                return McpToolResults.error("INVALID_PROTECTED_REQUEST");
            }
        }
        if (!executor.isRegisteredTarget(targetId)) {
            return McpToolResults.error("UNKNOWN_SIMULATION_TARGET");
        }
        var consume = new LinkedHashMap<String, Object>();
        consume.putAll(meta);
        consume.put("toolName", "simulation.set-state");
        consume.put("toolVersion", "1");
        consume.put("targetId", targetId);
        consume.put("parameters", Map.of("desiredState", desiredState));
        IncidentCallbackClient.ConsumeGrant grant;
        try {
            grant = callbacks.consume(consume);
        } catch (RuntimeException e) {
            return McpToolResults.error("TICKET_CONSUMPTION_UNAVAILABLE");
        }
        if (grant == null || !"GRANTED".equals(grant.decision())) {
            return McpToolResults.error("TICKET_NOT_GRANTED");
        }
        try (var trusted = McpObservation.capture().makeCurrent()) {
        McpObservation.identify("actionId", meta.get("actionId"));
        McpObservation.identify("incidentId", meta.get("incidentId"));
        McpObservation.identify("executionId", meta.get("executionId"));
        McpObservation.identify("dispatchRequestId", meta.get("requestId"));
        String executionKey = field(meta, "executionKey");
        long maximumMs = timeout.limitMillis(grant.maxExecutionDurationMs());
        var outcome = timeout.evaluate(() -> executor.apply(
                executionKey, targetId, desiredState, maximumMs), maximumMs,
                () -> executor.isApplicationAbsent(executionKey));
        if ("FAILED".equals(outcome.resultStatus())) {
            timeout.recordProvenRollback(executionKey, targetId);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("desiredState", desiredState);
        summary.put("applied", outcome.applied());
        if (outcome.proofCode() != null) summary.put("proofCode", outcome.proofCode());
        var receipt = new LinkedHashMap<String, Object>();
        receipt.put("schemaVersion", 1);
        receipt.put("receiptId", IdUtil.getSnowflakeNextIdStr());
        for (String name : Set.of("actionId", "incidentId", "executionId", "executionKey", "requestId")) {
            receipt.put(name, meta.get(name));
        }
        receipt.put("toolName", "simulation.set-state");
        receipt.put("toolVersion", "1");
        receipt.put("target", Map.of("type", "SIM_SERVICE", "id", targetId));
        receipt.put("resultStatus", outcome.resultStatus());
        receipt.put("resultCode", outcome.resultCode());
        receipt.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        receipt.put("summary", summary);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                callbacks.receipt(receipt);
                break;
            } catch (RuntimeException ignored) {
                // Return the same receipt through the response path after bounded callback attempts.
            }
        }
        return CallToolResult.builder().structuredContent(receipt)
                .addTextContent("simulation receipt " + receipt.get("receiptId"))
                .isError(false).build();
        }
    }

    private static String field(Map<String, Object> data, String key) {
        return data.get(key) instanceof String value ? value : null;
    }
}
