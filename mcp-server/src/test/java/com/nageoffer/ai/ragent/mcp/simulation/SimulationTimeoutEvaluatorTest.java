package com.nageoffer.ai.ragent.mcp.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SimulationTimeoutEvaluatorTest {
    @Test
    void timedOutExecutionReturnsUnknownWithoutSecondInvocation() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var evaluator = new SimulationTimeoutEvaluator(Duration.ofMillis(100));
        try {
            var outcome = evaluator.evaluate(() -> {
                calls.incrementAndGet();
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
                return new SimulationToolExecutor.Application("APPLIED", "READY", "PAUSED");
            });
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertEquals("UNKNOWN", outcome.resultStatus());
            assertEquals("TIMEOUT_UNPROVEN", outcome.resultCode());
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void completedApplicationIsSuccessfulAndUnexpectedFailureIsUnproven() {
        var evaluator = new SimulationTimeoutEvaluator(Duration.ofSeconds(1));
        assertEquals(50, evaluator.limitMillis(50));
        assertEquals(1000, evaluator.limitMillis(2000));
        assertEquals("SUCCESS", evaluator.evaluate(() ->
                new SimulationToolExecutor.Application("APPLIED", "READY", "PAUSED")).resultStatus());
        var failure = evaluator.evaluate(() -> { throw new IllegalStateException("lost commit response"); });
        assertEquals("UNKNOWN", failure.resultStatus());
        assertEquals("EXECUTION_UNPROVEN", failure.resultCode());
    }

    @Test
    void stoppedExecutionWithAbsentApplicationCanProveRollback() {
        var evaluator = new SimulationTimeoutEvaluator(Duration.ofMillis(100));
        var result = evaluator.evaluate(() -> {
            Thread.sleep(10_000);
            return new SimulationToolExecutor.Application("APPLIED", "READY", "PAUSED");
        }, () -> true);
        assertEquals("FAILED", result.resultStatus());
        assertEquals("TIMEOUT_PROVEN_ROLLED_BACK", result.resultCode());
        assertEquals("ROLLED_BACK", result.proofCode());
    }

    @Test
    void databaseStatementTimeoutRequiresReadableRollbackProof() {
        var evaluator = new SimulationTimeoutEvaluator(Duration.ofSeconds(1));
        var timeout = new SQLException("statement canceled", "57014");
        var proven = evaluator.evaluate(() -> { throw timeout; }, () -> true);
        assertEquals("FAILED", proven.resultStatus());
        assertEquals("TIMEOUT_PROVEN_ROLLED_BACK", proven.resultCode());
        var unreadable = evaluator.evaluate(() -> { throw timeout; }, () -> {
            throw new IllegalStateException("evidence table unavailable");
        });
        assertEquals("UNKNOWN", unreadable.resultStatus());
        assertEquals("TIMEOUT_UNPROVEN", unreadable.resultCode());
    }
}
