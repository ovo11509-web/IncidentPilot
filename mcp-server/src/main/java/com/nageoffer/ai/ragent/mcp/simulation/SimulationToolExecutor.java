package com.nageoffer.ai.ragent.mcp.simulation;

import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Component;

/** Only a registered target in the disposable demonstration database can be changed. */
@Component
public final class SimulationToolExecutor {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    public SimulationToolExecutor(DataSource bitDataSource, TransactionTemplate bitTransactionTemplate) {
        this.jdbc = new JdbcTemplate(bitDataSource);
        this.transactionManager = bitTransactionTemplate.getTransactionManager();
    }

    boolean isRegisteredTarget(String targetId) {
        return targetId != null && targetId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                && Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM simulation_target WHERE target_id=?)",
                        Boolean.class, targetId));
    }

    boolean isApplicationAbsent(String executionKey) {
        var proof = new TransactionTemplate(transactionManager);
        proof.setTimeout(1);
        return Boolean.TRUE.equals(proof.execute(status -> {
            // A locked or unavailable evidence table cannot delay an UNKNOWN receipt indefinitely.
            jdbc.execute("SET LOCAL statement_timeout = 500");
            return Boolean.FALSE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM simulation_application WHERE execution_key=?)",
                    Boolean.class, executionKey));
        }));
    }

    Application apply(String executionKey, String targetId, String desiredState) {
        return apply(executionKey, targetId, desiredState, 120_000);
    }

    Application apply(String executionKey, String targetId, String desiredState, long maximumMs) {
        if (executionKey == null || !executionKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || targetId == null || !targetId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || !Set.of("READY", "PAUSED").contains(desiredState)
                || maximumMs <= 0 || maximumMs > 120_000) {
            throw new IllegalArgumentException("invalid isolated simulation command");
        }
        var transactions = new TransactionTemplate(transactionManager);
        transactions.setTimeout((int) ((maximumMs + 999) / 1000));
        return transactions.execute(status -> {
            jdbc.execute("SET LOCAL statement_timeout = " + maximumMs);
            SqlRowSet target = jdbc.queryForRowSet("SELECT state,version FROM simulation_target "
                    + "WHERE target_id=? FOR UPDATE", targetId);
            if (!target.next()) throw new IllegalArgumentException("unregistered simulation target");
            SqlRowSet existing = jdbc.queryForRowSet("SELECT target_id,desired_state,before_state,after_state "
                    + "FROM simulation_application WHERE execution_key=?", executionKey);
            if (existing.next()) {
                if (!targetId.equals(existing.getString("target_id"))
                        || !desiredState.equals(existing.getString("desired_state"))) {
                    throw new IllegalStateException("execution key bound to another simulation command");
                }
                return new Application("DUPLICATE", existing.getString("before_state"),
                        existing.getString("after_state"));
            }
            String before = target.getString("state");
            long version = target.getLong("version");
            if (jdbc.update("UPDATE simulation_target SET state=?,version=version+1,"
                    + "updated_at=clock_timestamp() WHERE target_id=? AND version=?",
                    desiredState, targetId, version) != 1) throw new IllegalStateException("target version conflict");
            jdbc.update("INSERT INTO simulation_application (execution_key,target_id,desired_state,"
                    + "before_state,after_state,applied_at,result_summary) VALUES (?,?,?,?,?,"
                    + "clock_timestamp(),CAST(? AS jsonb))", executionKey, targetId, desiredState,
                    before, desiredState, "{\"applied\":true}");
            return new Application("APPLIED", before, desiredState);
        });
    }

    public record Application(String disposition, String beforeState, String afterState) {}
}
