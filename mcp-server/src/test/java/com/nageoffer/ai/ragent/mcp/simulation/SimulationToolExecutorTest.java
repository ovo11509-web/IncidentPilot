package com.nageoffer.ai.ragent.mcp.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

class SimulationToolExecutorTest {
    @Test
    void appliesOnlyRegisteredTargetAndOneRecordPerExecutionKey() throws Exception {
        try (var fixture = new SimulationTestFixture()) {
            var source = new DriverManagerDataSource(fixture.jdbcUrl(), fixture.username(), fixture.password());
            var executor = new SimulationToolExecutor(source,
                    new TransactionTemplate(new DataSourceTransactionManager(source)));
            assertEquals("APPLIED", executor.apply("execution-1", SimulationTestFixture.TARGET_ID,
                    "PAUSED").disposition());
            assertEquals("PAUSED", fixture.state());
            assertEquals("DUPLICATE", executor.apply("execution-1", SimulationTestFixture.TARGET_ID,
                    "PAUSED").disposition());
            assertEquals(1, fixture.applicationCount());
            try (var connection = fixture.connection(); var statement = connection.createStatement()) {
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO simulation_target(target_id,state) VALUES ('bad','RUNNING')"));
            }
            assertThrows(IllegalStateException.class,
                    () -> executor.apply("execution-1", SimulationTestFixture.TARGET_ID, "READY"));
            assertThrows(IllegalArgumentException.class,
                    () -> executor.apply("execution-2", "production", "PAUSED"));
            assertThrows(IllegalArgumentException.class,
                    () -> executor.apply("execution-2", SimulationTestFixture.TARGET_ID, "RUNNING"));
            assertEquals("PAUSED", fixture.state());
            assertEquals(1, fixture.applicationCount());
        }
    }

    @Test
    void lockedEvidenceQueryStopsBeforeUncertaintyCanBeMisclassified() throws Exception {
        try (var fixture = new SimulationTestFixture(); var lock = fixture.connection()) {
            var source = new DriverManagerDataSource(fixture.jdbcUrl(), fixture.username(), fixture.password());
            var executor = new SimulationToolExecutor(source,
                    new TransactionTemplate(new DataSourceTransactionManager(source)));
            lock.setAutoCommit(false);
            try (var statement = lock.createStatement()) {
                statement.execute("LOCK TABLE simulation_application IN ACCESS EXCLUSIVE MODE");
            }
            long started = System.nanoTime();
            assertThrows(DataAccessException.class, () -> executor.isApplicationAbsent("missing-key"));
            assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000);
            lock.rollback();
        }
    }
}
