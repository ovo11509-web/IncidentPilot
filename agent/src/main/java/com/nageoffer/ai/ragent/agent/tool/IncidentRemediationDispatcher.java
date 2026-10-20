package com.nageoffer.ai.ragent.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.config.ActionProperties;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** The only Incident Worker outbound path to the protected MCP transport. */
@Component
public final class IncidentRemediationDispatcher implements ActionDispatchWorker.DispatchPort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final TicketMapper tickets;
    private final ActionReceiptService receipts;
    private final ActionProperties properties;
    private final ProtectedCall call;

    @Autowired
    public IncidentRemediationDispatcher(TicketMapper tickets, ActionReceiptService receipts,
            ActionProperties properties) {
        this(tickets, receipts, properties, IncidentRemediationDispatcher::callProtected);
    }

    IncidentRemediationDispatcher(TicketMapper tickets, ActionReceiptService receipts,
            ActionProperties properties, ProtectedCall call) {
        this.tickets = tickets;
        this.receipts = receipts;
        this.properties = properties;
        this.call = call;
    }

    @Override
    public void dispatch(ActionDispatchWorker.DispatchClaim claim) {
        if (claim == null || !"simulation.set-state".equals(claim.toolName())
                || !"1".equals(claim.toolVersion())) throw new IllegalArgumentException("invalid dispatch");
        Map<String, Object> arguments;
        try {
            JsonNode parameters = JSON.readTree(claim.parametersJson());
            if (!parameters.isObject() || parameters.size() != 1
                    || !parameters.path("desiredState").isTextual()) {
                throw new IllegalArgumentException("invalid dispatch parameters");
            }
            arguments = Map.of("targetId", claim.targetId(),
                    "desiredState", parameters.path("desiredState").textValue());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid dispatch parameters");
        }
        var meta = Map.<String, Object>of("actionId", claim.actionId(), "incidentId", claim.incidentId(),
                "executionId", claim.executionId(), "executionKey", claim.executionKey(),
                "requestId", claim.requestId(), "ticket", claim.ticket(),
                "bindingVersion", claim.bindingVersion());
        String digest = sha256(claim.ticket());
        int attempts = 1 + properties.getPreConsumptionRetries();
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                CallToolResult result;
                try (var attemptScope = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.mcp.dispatch", io.opentelemetry.api.trace.SpanKind.CLIENT)) {
                    com.nageoffer.ai.ragent.incident.observability.IncidentObservation.dispatch(claim);
                    attemptScope.id("attempt", attempt + 1);
                    result = call.invoke(arguments, meta);
                    attemptScope.outcome(result != null && !Boolean.TRUE.equals(result.isError()) ? "RESPONSE" : "REJECTED");
                }
                if (result != null && result.structuredContent() != null) {
                    receipts.acceptFromWorker(parseReceipt(result.structuredContent()));
                }
                return;
            } catch (ActionReceiptService.ConflictException e) {
                return; // conflict evidence is already committed by the receipt service
            } catch (RuntimeException e) {
                if (attempt + 1 >= attempts
                        || !tickets.canRetryBeforeConsumption(claim.executionId(), digest)) {
                    return; // an unknown or consumed ticket is never dispatched again
                }
            }
        }
    }

    private static CallToolResult callProtected(Map<String, Object> arguments, Map<String, Object> meta) {
        String endpoint = System.getenv("INCIDENT_MCP_PROTECTED_URL");
        String token = System.getenv("INCIDENT_MCP_OUTBOUND_TOKEN");
        if (endpoint == null || token == null || token.length() < 32) {
            throw new IllegalStateException("protected MCP identity unavailable");
        }
        URI uri = URI.create(endpoint);
        ActionServiceAuthentication.requireSafeEndpoint(uri);
        if (!"/mcp/incident".equals(uri.getPath())) {
            throw new IllegalArgumentException("invalid protected MCP path");
        }
        var origin = uri.getScheme() + "://" + uri.getRawAuthority();
        var parent = io.opentelemetry.context.Context.current();
        var transport = HttpClientStreamableHttpTransport.builder(origin)
                .endpoint("/mcp/incident")
                .customizeRequest(request -> {
                    request.header("Authorization", "Bearer " + token);
                    com.nageoffer.ai.ragent.framework.observability.ObservationScope.inject(parent, request::header);
                })
                .connectTimeout(Duration.ofSeconds(3)).build();
        try (var client = McpClient.sync(transport)
                .clientInfo(new Implementation("incident-dispatcher", "1"))
                .requestTimeout(Duration.ofMinutes(2)).build()) {
            client.initialize();
            return client.callTool(CallToolRequest.builder().name("simulation.set-state")
                    .arguments(arguments).meta(meta).build());
        }
    }

    private static ActionReceiptService.Submission parseReceipt(Object structured) {
        try {
            JsonNode root = JSON.valueToTree(structured);
            var target = root.path("target");
            return new ActionReceiptService.Submission(root.path("schemaVersion").asInt(-1),
                    text(root, "receiptId"), text(root, "actionId"), text(root, "incidentId"),
                    text(root, "executionId"), text(root, "executionKey"), text(root, "requestId"),
                    text(root, "toolName"), text(root, "toolVersion"), text(target, "type"),
                    text(target, "id"), text(root, "resultStatus"), text(root, "resultCode"),
                    OffsetDateTime.parse(text(root, "occurredAt")), root.path("summary").toString());
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid protected MCP receipt");
        }
    }

    private static String text(JsonNode node, String name) {
        if (!node.path(name).isTextual()) throw new IllegalArgumentException("invalid receipt field");
        return node.path(name).textValue();
    }

    private static String sha256(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(HexFormat.of().parseHex(secret)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    interface ProtectedCall {
        CallToolResult invoke(Map<String, Object> arguments, Map<String, Object> meta);
    }
}
