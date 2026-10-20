package com.nageoffer.ai.ragent.mcp.simulation;

import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Service-authenticated read-only evidence endpoint, separate from MCP tool invocation. */
@RestController
public final class SimulationReconciliationController {
    private final ProtectedServiceAuthentication authentication;
    private final SimulationReconciliationReader reader;

    public SimulationReconciliationController(ProtectedServiceAuthentication authentication,
            SimulationReconciliationReader reader) {
        this.authentication = authentication;
        this.reader = reader;
    }

    @PostMapping("/mcp/incident/reconcile")
    public ResponseEntity<?> reconcile(@RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> request) {
        if (!authentication.isAuthenticated(authorization)) return ResponseEntity.status(401).build();
        if (request == null || !request.keySet().equals(Set.of("executionId", "executionKey", "targetId"))
                || !(request.get("executionId") instanceof String executionId)
                || !(request.get("executionKey") instanceof String executionKey)
                || !(request.get("targetId") instanceof String targetId)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(reader.read(executionId, executionKey, targetId));
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.badRequest().build();
        }
    }
}
