package com.nageoffer.ai.ragent.mcp.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SimulationReconciliationReaderTest {
    @Test
    void applicationEvidenceSurvivesLaterTargetChangeAndAbsenceNeedsStopProof() throws Exception {
        try (var fixture = new SimulationTestFixture()) {
            var transactions = new TransactionTemplate(new DataSourceTransactionManager(fixture.dataSource()));
            var executor = new SimulationToolExecutor(fixture.dataSource(), transactions);
            var timeout = new SimulationTimeoutEvaluator(Duration.ofSeconds(1));
            var reader = new SimulationReconciliationReader(fixture.dataSource(), timeout);
            assertEquals("INDETERMINATE", reader.read("300", "key-1",
                    SimulationTestFixture.TARGET_ID).status());
            executor.apply("key-1", SimulationTestFixture.TARGET_ID, "PAUSED");
            executor.apply("key-2", SimulationTestFixture.TARGET_ID, "READY");
            var applied = reader.read("300", "key-1", SimulationTestFixture.TARGET_ID);
            assertEquals("APPLIED", applied.status());
            assertEquals("READY", applied.beforeState());
            assertEquals("PAUSED", applied.afterState());
            assertEquals("READY", fixture.state());
            assertEquals("INDETERMINATE", reader.read("301", "key-3",
                    SimulationTestFixture.TARGET_ID).status());
            timeout.recordProvenRollback("key-3", SimulationTestFixture.TARGET_ID);
            assertEquals("PROVEN_NOT_APPLIED", reader.read("301", "key-3",
                    SimulationTestFixture.TARGET_ID).status());
            assertEquals("INDETERMINATE", reader.read("301", "key-3", "other").status());
            assertEquals(2, fixture.applicationCount());
        }
    }
}
