package com.nageoffer.ai.ragent.mcp.simulation;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads only the isolated target and committed applications; missing evidence stays uncertain. */
@Component
public final class SimulationReconciliationReader {
    private final JdbcTemplate jdbc;
    private final SimulationTimeoutEvaluator timeout;

    public SimulationReconciliationReader(DataSource bitDataSource, SimulationTimeoutEvaluator timeout) {
        this.jdbc = new JdbcTemplate(bitDataSource);
        this.timeout = timeout;
    }

    public Evidence read(String executionId, String executionKey, String targetId) {
        if (executionId == null || !executionId.matches("[0-9]{1,20}")
                || executionKey == null || !executionKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || targetId == null || !targetId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("invalid reconciliation identity");
        }
        try {
            List<String> targets = jdbc.query("SELECT state FROM simulation_target WHERE target_id=?",
                    (row, index) -> row.getString(1), targetId);
            if (targets.size() != 1) return evidence("INDETERMINATE", executionId, executionKey,
                    targetId, null, null, null, null);
            var applications = jdbc.query("SELECT target_id, desired_state, before_state, after_state "
                    + "FROM simulation_application WHERE execution_key=?", (row, index) ->
                    new ApplicationEvidence(row.getString(1), row.getString(2),
                            row.getString(3), row.getString(4)), executionKey);
            if (applications.size() == 1) {
                var app = applications.get(0);
                if (targetId.equals(app.targetId()) && app.desiredState().equals(app.afterState())) {
                    return evidence("APPLIED", executionId, executionKey, targetId,
                            targets.get(0), app.beforeState(), app.afterState(),
                            "simulation-application:" + executionKey);
                }
                return evidence("INDETERMINATE", executionId, executionKey,
                        targetId, targets.get(0), null, null, null);
            }
            if (timeout.hasProvenRollback(executionKey, targetId)) {
                return evidence("PROVEN_NOT_APPLIED", executionId, executionKey,
                        targetId, targets.get(0), null, null,
                        "simulation-rollback:" + executionKey);
            }
            return evidence("INDETERMINATE", executionId, executionKey,
                    targetId, targets.get(0), null, null, null);
        } catch (DataAccessException unavailable) {
            return evidence("INDETERMINATE", executionId, executionKey,
                    targetId, null, null, null, null);
        }
    }

    private static Evidence evidence(String status, String executionId, String executionKey,
            String targetId, String currentState, String beforeState, String afterState,
            String evidenceRef) {
        return new Evidence(status, executionId, executionKey, targetId,
                currentState, beforeState, afterState, evidenceRef);
    }

    private record ApplicationEvidence(String targetId, String desiredState,
            String beforeState, String afterState) {}

    public record Evidence(String status, String executionId, String executionKey,
            String targetId, String currentState, String beforeState, String afterState,
            String evidenceRef) {}
}
