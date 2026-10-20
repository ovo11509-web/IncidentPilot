/* Licensed under the Apache License, Version 2.0. See LICENSE in the project root. */
package com.nageoffer.ai.ragent;

import static org.junit.jupiter.api.Assertions.*;
import static com.nageoffer.ai.ragent.BootstrapIsolatedStartupTest.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.alert.security.SourceHmacVerifier;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Actual HTTP and process/database faults; test classpath JVMs, not executable-JAR claims. */
class IncidentReliabilityRuntimeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test
    void httpIdentityProcessRediscoveryDatabaseRecoveryAndFourBaselines() throws Exception {
        runIsolatedApplications(false, "reliability-runtime");
    }

    static Process run(int port, PostgreSQLContainer db, PostgreSQLContainer simulation, Process app,
            ProcessBuilder builder, String secret, Path proof, Path restartLog) throws Exception {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("evidenceLevel", "REAL_HTTP_PG_TEST_CLASSPATH_JVM");
        facts.put("runId", System.getProperty("lab.runId", "standalone"));
        facts.put("sourceDigest", System.getProperty("lab.sourceDigest", "standalone"));
        facts.put("java", System.getProperty("java.runtime.version"));
        facts.put("processors", Runtime.getRuntime().availableProcessors());
        facts.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        facts.put("image", db.getDockerImageName());
        facts.put("pollIntervalMs", 100);
        facts.put("limitations", "local closed-loop baseline; no production SLO, broker, model or JAR fault claim");
        try {
            byte[] duplicate = payload("rel-duplicate", "identity", "fixed");
            AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger();
            List<Sample> repetitions = batch(port, secret, Collections.nCopies(40, duplicate), 10, active, peak);
            assertTrue(repetitions.stream().allMatch(s -> s.status == 202));
            assertEquals(1, repetitions.stream().filter(s -> !s.duplicate).count());
            assertEquals(39, repetitions.stream().filter(s -> s.duplicate).count());
            assertEquals(1, scalar(db, "SELECT count(*) FROM t_incident_alert WHERE external_alert_id='rel-duplicate'"));
            String acceptedId = repetitions.get(0).alertId;
            assertTrue(repetitions.stream().allMatch(s -> acceptedId.equals(s.alertId)));
            var conflict = submit(port, secret, payload("rel-duplicate", "identity", "different"));
            assertEquals(409, conflict.status);
            assertEquals(1, scalar(db, "SELECT count(*) FROM t_incident_alert WHERE external_alert_id='rel-duplicate'"));
            facts.put("httpIdentity", Map.of("attempts", 40, "workers", 10, "peakActive", peak.get(),
                    "createdResponses", 1, "duplicateResponses", 39, "http202", 40, "conflicts", 1, "persisted", 1));

            var pending = submit(port, secret, payload("rel-before-kill", "recovery", "pending"));
            assertEquals(202, pending.status);
            assertEquals("RECEIVED", status(db, pending.alertId));
            var deferred = submit(port, secret, payload("rel-before-db-pause", "db-backlog", "pending"));
            assertEquals(202, deferred.status);
            execute(db, "UPDATE t_incident_alert SET next_attempt_at=clock_timestamp()+interval '1 hour' WHERE id='" + deferred.alertId + "'");
            long oldPid = app.pid();
            app.destroyForcibly();
            assertTrue(app.waitFor(15, TimeUnit.SECONDS));
            List<String> command = new ArrayList<>(builder.command());
            assertTrue(command.remove("--incident.worker.enabled=false"));
            command.add("--incident.worker.enabled=true");
            builder.command(command).redirectOutput(restartLog.toFile());
            app = builder.start();
            assertNotEquals(oldPid, app.pid());
            long recoveryStart = System.nanoTime();
            awaitProcessed(db, List.of(pending.alertId, acceptedId), 90);
            assertEquals(1, scalar(db, "SELECT count(*) FROM t_incident_timeline WHERE event_type IN "
                    + "('ALERT_ATTACHED','LATE_ALERT_ATTACHED_AFTER_SEAL') AND related_entity_id='" + pending.alertId + "'"));
            facts.put("processRecovery", Map.of("oldPid", oldPid, "newPid", app.pid(), "resubmissions", 0,
                    "acceptedBeforeKill", 1, "processed", 1, "observedRecoveryMs", elapsedMs(recoveryStart)));

            boolean paused = false;
            try {
                assertEquals("RECEIVED", status(db, deferred.alertId));
                execute(db, "UPDATE t_incident_alert SET next_attempt_at=clock_timestamp()+interval '2 seconds' WHERE id='" + deferred.alertId + "'");
                db.getDockerClient().pauseContainerCmd(db.getContainerId()).exec();
                paused = true;
                assertTrue(Boolean.TRUE.equals(db.getDockerClient().inspectContainerCmd(db.getContainerId()).exec().getState().getPaused()));
                Thread.sleep(2500); // Owned pending task becomes due while its database is paused.
                String unavailableResult;
                try {
                    var unavailable = submit(port, secret, payload("rel-during-db-fault", "db-fault", "fixed"));
                    assertTrue(unavailable.status == 0 || unavailable.status >= 500, "unavailable database must not report acceptance");
                    unavailableResult = unavailable.status == 0 ? "TRANSPORT_UNKNOWN" : "HTTP_" + unavailable.status;
                } catch (java.io.IOException unavailable) {
                    // A lost/timed-out response is indeterminate, not evidence of rollback.
                    unavailableResult = "TRANSPORT_UNKNOWN";
                }
                facts.put("databaseFault", new LinkedHashMap<>(Map.of("pausedVerified", true,
                        "duringFault", unavailableResult, "responseUnknownNotAssumedRollback", true)));
            } finally {
                if (paused) db.getDockerClient().unpauseContainerCmd(db.getContainerId()).exec();
            }
            assertFalse(Boolean.TRUE.equals(db.getDockerClient().inspectContainerCmd(db.getContainerId()).exec().getState().getPaused()));
            var retry = retrySubmission(port, secret, payload("rel-during-db-fault", "db-fault", "fixed"));
            awaitProcessed(db, List.of(retry.alertId, deferred.alertId), 60);
            assertEquals(1, scalar(db, "SELECT count(*) FROM t_incident_alert WHERE external_alert_id='rel-during-db-fault'"));
            facts.put("databaseRecovery", Map.of("resumedVerified", true, "persistedUnique", 1, "processed", 1,
                    "acceptedBacklogRecovered", 1, "backlogResubmissions", 0, "dueTimeControlledFixture", true));

            List<Map<String, Object>> baselines = new ArrayList<>();
            facts.put("baselines", baselines);
            for (String mode : List.of("hot", "multi")) {
                for (int history : List.of(0, 500)) {
                    Map<String, Object> measured = baseline(port, secret, db, mode, history);
                    baselines.add(measured);
                    assertEquals(100L, measured.get("accepted"), "all declared identities must be accepted");
                    assertEquals(100, measured.get("processed"), "failed/unfinished samples block acceptance");
                    assertEquals(0L, measured.get("failed"));
                }
            }
            assertEquals(4, baselines.size());
            assertEquals(400, baselines.stream().mapToInt(b -> (int)b.get("sent")).sum());
            facts.put("baselines", baselines);
            assertEquals(0, count(simulation, "simulation_application"));
            facts.put("simulationApplications", 0);
            facts.put("state", "PASS");
            return app;
        } catch (Throwable failure) {
            // New process must also be owned when callback cannot return it to the enclosing fixture.
            if (app.isAlive()) { app.destroyForcibly(); app.waitFor(15, TimeUnit.SECONDS); }
            facts.put("state", "FAIL");
            throw failure;
        } finally {
            Files.writeString(proof.resolve("reliability-runtime.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(facts));
        }
    }

    private static Map<String, Object> baseline(int port, String secret, PostgreSQLContainer db,
            String mode, int history) throws Exception {
        String prefix = "rel-" + mode + "-" + history + "-";
        List<byte[]> historyPayloads = new ArrayList<>();
        for (int i = 0; i < history; i++) historyPayloads.add(payload(prefix + "history-" + i,
                prefix + (mode.equals("hot") ? "key" : i % 10), "history"));
        List<Sample> historical = batch(port, secret, historyPayloads, 10, new AtomicInteger(), new AtomicInteger());
        assertTrue(historical.stream().allMatch(s -> s.status == 202));
        awaitProcessed(db, historical.stream().map(s -> s.alertId).toList(), 120);
        List<byte[]> inputs = new ArrayList<>();
        for (int i = 0; i < 100; i++) inputs.add(payload(prefix + "sample-" + i,
                prefix + (mode.equals("hot") ? "key" : i % 10), "sample"));
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger();
        long start = System.nanoTime();
        var tracked = new ConcurrentLinkedQueue<Sample>();
        var submitter = Executors.newSingleThreadExecutor();
        List<Sample> samples;
        Map<String, Double> completions;
        try {
            var sent = submitter.submit(() -> batch(port, secret, inputs, 10, active, peak, tracked::add));
            completions = observeCompletion(db, tracked, sent::isDone, 120);
            samples = sent.get(180, TimeUnit.SECONDS);
        } finally { submitter.shutdownNow(); assertTrue(submitter.awaitTermination(15, TimeUnit.SECONDS)); }
        assertEquals(100, samples.size());
        long accepted = samples.stream().filter(s -> s.status == 202).count();
        double duration = elapsedMs(start);
        long failed = scalar(db, "SELECT count(*) FROM t_incident_alert WHERE external_alert_id LIKE '" + prefix + "sample-%' AND processing_status='FAILED'");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode); result.put("history", history); result.put("historyPerKey", mode.equals("hot") ? history : history / 10);
        result.put("sent", 100); result.put("workers", 10); result.put("peakActive", peak.get());
        result.put("accepted", accepted); result.put("processed", completions.size()); result.put("failed", failed);
        result.put("unfinishedOrUnaccepted", 100 - completions.size() - failed);
        result.put("batchMs", duration); result.put("completedPerSecond", completions.size() * 1000.0 / duration);
        result.put("receiveMsAllResponses", percentiles(samples.stream().map(s -> s.receiveMs).toList()));
        result.put("observedCompletionMsCompletedSubset", percentiles(new ArrayList<>(completions.values())));
        result.put("sampleStatuses", samples.stream().map(s -> Map.of("externalId", s.externalId, "status", s.status,
                "receiveMs", s.receiveMs, "processed", completions.containsKey(s.alertId))).toList());
        return result;
    }

    private static Map<String, Double> observeCompletion(PostgreSQLContainer db, ConcurrentLinkedQueue<Sample> samples,
            java.util.function.BooleanSupplier submissionsDone, int seconds) throws Exception {
        Map<String, Sample> pending = new HashMap<>();
        Map<String, Double> results = new HashMap<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        do {
            Sample received;
            while ((received = samples.poll()) != null) {
                if (received.status == 202 || received.status == 200) pending.put(received.alertId, received);
            }
            try (var c = java.sql.DriverManager.getConnection(db.getJdbcUrl() + (db.getJdbcUrl().contains("?") ? "&" : "?") + "connectTimeout=2&socketTimeout=5", db.getUsername(), db.getPassword());
                    var stmt = c.createStatement()) {
                stmt.setQueryTimeout(5);
                try (var rows = stmt.executeQuery("SELECT id FROM t_incident_alert WHERE source_id='diagnosis-demo' AND processing_status='PROCESSED'")) {
                    while (rows.next()) {
                        String id = rows.getString(1); Sample sample = pending.remove(id);
                        if (sample != null) results.put(id, elapsedMs(sample.started));
                    }
                }
            }
            if (!pending.isEmpty() || !submissionsDone.getAsBoolean()) Thread.sleep(100);
        } while ((!submissionsDone.getAsBoolean() || !samples.isEmpty() || !pending.isEmpty()) && System.nanoTime() < deadline);
        return results;
    }

    private static void awaitProcessed(PostgreSQLContainer db, List<String> ids, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        Set<String> remaining = new HashSet<>(ids);
        while (!remaining.isEmpty() && System.nanoTime() < deadline) {
            remaining.removeIf(id -> {
                try { return "PROCESSED".equals(status(db, id)); } catch (Exception unavailable) { return false; }
            });
            if (!remaining.isEmpty()) Thread.sleep(100);
        }
        assertTrue(remaining.isEmpty(), "accepted identities must recover within declared test wait");
    }

    private static Sample retrySubmission(int port, String secret, byte[] body) throws Exception {
        for (int i = 0; i < 20; i++) {
            try {
                Sample result = submit(port, secret, body);
                if (result.status == 202 || result.status == 200) return result;
            } catch (java.io.IOException unavailable) { }
            Thread.sleep(500);
        }
        throw new AssertionError("database recovery did not restore ingestion");
    }

    private static List<Sample> batch(int port, String secret, List<byte[]> payloads, int workers,
            AtomicInteger active, AtomicInteger peak) throws Exception {
        return batch(port, secret, payloads, workers, active, peak, sample -> { });
    }

    private static List<Sample> batch(int port, String secret, List<byte[]> payloads, int workers,
            AtomicInteger active, AtomicInteger peak, java.util.function.Consumer<Sample> onResponse) throws Exception {
        var pool = Executors.newFixedThreadPool(workers);
        var start = new CountDownLatch(1);
        try {
            List<Future<Sample>> tasks = new ArrayList<>();
            for (byte[] body : payloads) tasks.add(pool.submit(() -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                try { Sample response = submit(port, secret, body); onResponse.accept(response); return response; }
                finally { active.decrementAndGet(); }
            }));
            start.countDown();
            List<Sample> result = new ArrayList<>();
            for (var task : tasks) result.add(task.get(180, TimeUnit.SECONDS));
            return result;
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }

    private static byte[] payload(String id, String rule, String title) throws Exception {
        return JSON.writeValueAsBytes(Map.of("externalAlertId", id, "eventType", "FIRING", "occurredAt", "2026-01-01T00:00:00Z",
                "ruleId", rule, "title", title, "severity", "HIGH", "environment", "demo", "service", "incident-demo",
                "resource", Map.of("type", "sim_service", "id", "demo")));
    }

    private static Sample submit(int port, String secret, byte[] body) throws Exception {
        String path = "/incident/v1/alerts", stamp = Long.toString(Instant.now().getEpochSecond());
        byte[] signature = SourceHmacVerifier.hmac(secret.getBytes(StandardCharsets.UTF_8),
                SourceHmacVerifier.frame("1", "diagnosis-demo", stamp, "POST", path, body));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/ragent" + path))
                .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
                .header("X-Incident-Protocol-Version", "1").header("X-Incident-Source-Id", "diagnosis-demo")
                .header("X-Incident-Key-Id", "key1").header("X-Incident-Request-Timestamp", stamp)
                .header("X-Incident-Signature", "sha256=" + HexFormat.of().formatHex(signature))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        long started = System.nanoTime();
        HttpResponse<String> response;
        try { response = HTTP.send(request, HttpResponse.BodyHandlers.ofString()); }
        catch (java.io.IOException transportFailure) {
            return new Sample(JSON.readTree(body).path("externalAlertId").asText(), "", 0, false, started, elapsedMs(started));
        }
        String id = (response.statusCode() == 202 || response.statusCode() == 200)
                ? JSON.readTree(response.body()).path("data").path("alertId").asText() : "";
        if (response.statusCode() == 202 || response.statusCode() == 200) assertFalse(id.isBlank());
        boolean duplicate = (response.statusCode() == 202 || response.statusCode() == 200)
                && JSON.readTree(response.body()).path("data").path("duplicate").asBoolean();
        return new Sample(JSON.readTree(body).path("externalAlertId").asText(), id, response.statusCode(), duplicate, started, elapsedMs(started));
    }

    private static String status(PostgreSQLContainer db, String id) throws Exception {
        return value(db, "SELECT processing_status FROM t_incident_alert WHERE id='" + id + "'");
    }
    private static long scalar(PostgreSQLContainer db, String query) throws Exception { return Long.parseLong(value(db, query)); }
    private static double elapsedMs(long start) { return (System.nanoTime() - start) / 1_000_000.0; }
    private static Map<String, Object> percentiles(List<Double> values) {
        if (values.isEmpty()) return Map.of("samples", 0);
        List<Double> sorted = values.stream().sorted().toList();
        return Map.of("samples", sorted.size(), "p50", sorted.get((int)Math.ceil(sorted.size() * .5) - 1),
                "p95", sorted.get((int)Math.ceil(sorted.size() * .95) - 1), "p99", sorted.get((int)Math.ceil(sorted.size() * .99) - 1));
    }
    private record Sample(String externalId, String alertId, int status, boolean duplicate, long started, double receiveMs) {}
}
