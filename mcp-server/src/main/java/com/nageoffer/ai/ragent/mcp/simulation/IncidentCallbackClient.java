package com.nageoffer.ai.ragent.mcp.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Separate MCP-to-Incident identity; no callback state is cached as execution permission. */
@Component
public class IncidentCallbackClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http;
    private final Map<String, String> environment;

    public IncidentCallbackClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(), System.getenv());
    }

    IncidentCallbackClient(HttpClient http, Map<String, String> environment) {
        this.http = http;
        this.environment = environment;
    }

    public ConsumeGrant consume(Map<String, Object> request) {
        try {
            var response = post("/incident/internal/v1/action-tickets/consume", request);
            var data = JSON.readTree(response).path("data");
            if (!"SUCCESS".equals(JSON.readTree(response).path("code").asText())
                    || !data.path("decision").isTextual()) throw new IllegalStateException("callback unavailable");
            long max = data.path("maxExecutionDurationMs").asLong(-1);
            if ("GRANTED".equals(data.path("decision").asText()) && max <= 0)
                throw new IllegalStateException("missing execution duration");
            return new ConsumeGrant(data.path("decision").asText(), max);
        } catch (Exception e) {
            throw new IllegalStateException("ticket consumption unavailable");
        }
    }

    public record ConsumeGrant(String decision, long maxExecutionDurationMs) {}

    public void receipt(Map<String, Object> receipt) {
        try {
            var response = JSON.readTree(post("/incident/internal/v1/action-receipts", receipt));
            if (!"SUCCESS".equals(response.path("code").asText())) {
                throw new IllegalStateException("receipt callback unavailable");
            }
        } catch (Exception e) {
            throw new IllegalStateException("receipt callback unavailable");
        }
    }

    private String post(String path, Map<String, Object> payload) throws Exception {
        var rootText = environment.get("INCIDENT_MCP_CALLBACK_BASE_URL");
        var token = environment.get("INCIDENT_MCP_CALLBACK_TOKEN");
        if (rootText == null || token == null || token.length() < 32) {
            throw new IllegalStateException("callback identity unavailable");
        }
        var root = URI.create(rootText);
        ProtectedServiceAuthentication.requireSafeEndpoint(root);
        String prefix = root.getPath();
        if ((!prefix.isEmpty() && !"/".equals(prefix) && !"/api/ragent".equals(prefix))
                || root.getRawQuery() != null || root.getRawFragment() != null) {
            throw new IllegalArgumentException("invalid callback root");
        }
        var uri = URI.create(root.getScheme() + "://" + root.getRawAuthority()
                + ("/api/ragent".equals(prefix) ? prefix : "") + path);
        try (var observation = McpObservation.start(path.endsWith("consume") ? "incident.callback.consume" : "incident.callback.receipt", io.opentelemetry.api.trace.SpanKind.CLIENT)) {
        var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)));
        McpObservation.inject(io.opentelemetry.context.Context.current(), builder::header);
        var request = builder.build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("callback rejected");
        observation.outcome("ACCEPTED");
        return response.body();
        }
    }
}
