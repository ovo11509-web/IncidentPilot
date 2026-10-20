package com.nageoffer.ai.ragent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.service.ActionReconciliationService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Main-service identity to the protected read-only simulation evidence endpoint. */
@Component
public final class IncidentReconciliationClient implements ActionReconciliationService.EvidencePort {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Override
    public ActionReconciliationService.Evidence read(String executionId, String executionKey,
            String targetId) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.callback.reconcile", io.opentelemetry.api.trace.SpanKind.CLIENT)) {
        var endpoint = System.getenv("INCIDENT_MCP_PROTECTED_URL");
        var token = System.getenv("INCIDENT_MCP_OUTBOUND_TOKEN");
        if (endpoint == null || token == null || token.length() < 32)
            throw new IllegalStateException("protected evidence identity unavailable");
        var uri = URI.create(endpoint);
        ActionServiceAuthentication.requireSafeEndpoint(uri);
        if (!"/mcp/incident".equals(uri.getPath()) || uri.getQuery() != null
                || uri.getFragment() != null) throw new IllegalArgumentException("invalid protected evidence path");
        try {
            var builder = HttpRequest.newBuilder(URI.create(endpoint + "/reconcile"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                            Map.of("executionId", executionId, "executionKey", executionKey,
                                    "targetId", targetId))));
            com.nageoffer.ai.ragent.framework.observability.ObservationScope.inject(io.opentelemetry.context.Context.current(), builder::header);
            var request = builder.build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IllegalStateException("protected evidence unavailable");
            return JSON.readValue(response.body(), ActionReconciliationService.Evidence.class);
        } catch (Exception unavailable) {
            throw new IllegalStateException("protected evidence unavailable");
        }

        }
    }
}
