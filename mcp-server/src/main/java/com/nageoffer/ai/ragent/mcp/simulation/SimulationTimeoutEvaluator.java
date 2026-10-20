package com.nageoffer.ai.ragent.mcp.simulation;

import java.time.Duration;
import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Component;

/** Bounded wait for one isolated write; uncertainty is never reported as a proven failure. */
@Component
public final class SimulationTimeoutEvaluator {
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS,
            new SynchronousQueue<>(), job -> {
                var thread = new Thread(job, "simulation-write-" + THREAD_NUMBER.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final Duration maximum;
    private final ConcurrentHashMap<String, String> provenRollback = new ConcurrentHashMap<>();

    public SimulationTimeoutEvaluator() {
        this(Duration.ofMinutes(2));
    }

    SimulationTimeoutEvaluator(Duration maximum) {
        if (maximum == null || maximum.isZero() || maximum.isNegative())
            throw new IllegalArgumentException("invalid simulation duration");
        this.maximum = maximum;
    }

    public Outcome evaluate(Callable<SimulationToolExecutor.Application> operation) {
        return evaluate(operation, maximum.toMillis(), () -> false);
    }

    public Outcome evaluate(Callable<SimulationToolExecutor.Application> operation,
            BooleanSupplier stoppedAndRolledBack) {
        return evaluate(operation, maximum.toMillis(), stoppedAndRolledBack);
    }

    public long limitMillis(long registeredMaximumMs) {
        if (registeredMaximumMs <= 0) throw new IllegalArgumentException("invalid registered duration");
        return Math.min(maximum.toMillis(), registeredMaximumMs);
    }

    public Outcome evaluate(Callable<SimulationToolExecutor.Application> operation,
            long boundedMaximumMs, BooleanSupplier stoppedAndRolledBack) {
        if (boundedMaximumMs <= 0 || boundedMaximumMs > maximum.toMillis())
            throw new IllegalArgumentException("invalid execution duration");
        Future<SimulationToolExecutor.Application> result;
        var finished = new CountDownLatch(1);
        var captured = McpObservation.capture();
        try {
            result = WORK.submit(() -> {
                try (var context = captured.makeCurrent();
                     var observation = McpObservation.start("incident.mcp.execute", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
                    var application = operation.call();
                    observation.outcome("COMPLETED");
                    return application;
                } finally {
                    finished.countDown();
                }
            });
        } catch (RuntimeException unavailable) {
            return new Outcome("UNKNOWN", "EXECUTION_UNPROVEN", null, null);
        }
        try {
            result.get(boundedMaximumMs, TimeUnit.MILLISECONDS);
            return new Outcome("SUCCESS", "SIMULATION_APPLIED", true, null);
        } catch (TimeoutException timeout) {
            result.cancel(true);
            try {
                if (finished.await(1, TimeUnit.SECONDS) && stoppedAndRolledBack.getAsBoolean()) {
                    return new Outcome("FAILED", "TIMEOUT_PROVEN_ROLLED_BACK", false, "ROLLED_BACK");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException unavailable) {
                // Evidence unavailable is uncertainty, not failure proof.
            }
            return new Outcome("UNKNOWN", "TIMEOUT_UNPROVEN", null, null);
        } catch (InterruptedException interrupted) {
            result.cancel(true);
            Thread.currentThread().interrupt();
            return new Outcome("UNKNOWN", "EXECUTION_UNPROVEN", null, null);
        } catch (ExecutionException failure) {
            if (statementTimedOut(failure)) {
                try {
                    if (stoppedAndRolledBack.getAsBoolean()) {
                        return new Outcome("FAILED", "TIMEOUT_PROVEN_ROLLED_BACK", false, "ROLLED_BACK");
                    }
                } catch (RuntimeException unavailable) {
                    // A timed-out statement without readable evidence remains uncertain.
                }
                return new Outcome("UNKNOWN", "TIMEOUT_UNPROVEN", null, null);
            }
            return new Outcome("UNKNOWN", "EXECUTION_UNPROVEN", null, null);
        }
    }

    private static boolean statementTimedOut(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "57014".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    public record Outcome(String resultStatus, String resultCode, Boolean applied, String proofCode) {}

    void recordProvenRollback(String executionKey, String targetId) {
        if (executionKey == null || targetId == null) throw new IllegalArgumentException("invalid proof identity");
        provenRollback.putIfAbsent(executionKey, targetId);
    }

    boolean hasProvenRollback(String executionKey, String targetId) {
        return targetId.equals(provenRollback.get(executionKey));
    }
}
