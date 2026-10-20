/* Licensed under the Apache License, Version 2.0. See LICENSE in the project root. */
package com.nageoffer.ai.ragent.incident.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.evidence.EvidenceManifestWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;

/** Independent, declared oracle; never calls the production partitioner to obtain expectations. */
class IncidentReliabilityAcceptanceTest {
    private static IncidentDbFixture db;
    private static JsonNode dataset;
    private static final List<Map<String, Object>> results = new ArrayList<>();
    private static final Set<String> REQUIRED = Set.of("single", "before-window", "at-window", "after-window",
            "chain", "gap-then-chain", "three-episodes", "different-key", "interleaved-key", "failed-anchor",
            "all-failed", "failed-bridge", "seal-equality", "preseal-late", "failed-separate");

    @BeforeAll
    static void setup() throws Exception {
        dataset = new ObjectMapper().readTree(Files.readString(Path.of("src/test/resources/fixtures/reliability/aggregation-cases.json")));
        assertEquals(1, dataset.path("schemaVersion").asInt());
        Set<String> actual = new HashSet<>();
        dataset.path("cases").forEach(c -> assertTrue(actual.add(c.path("id").asText())));
        assertEquals(REQUIRED, actual);
        assertEquals(List.of(7, 23, 91), indices(dataset.path("seeds")));
        db = new IncidentDbFixture();
    }

    @TestFactory
    Stream<DynamicTest> declaredCasesConvergeUnderThreeProcessingOrders() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : dataset.path("cases")) {
            for (int seed : indices(dataset.path("seeds"))) {
                tests.add(DynamicTest.dynamicTest(c.path("id").asText() + "/seed=" + seed, () -> run(c, seed)));
            }
        }
        assertEquals(45, tests.size());
        return tests.stream();
    }

    private static void run(JsonNode c, int seed) throws Exception {
        // Only this test-owned disposable database; no application or demo database is accessed.
        db.sql("TRUNCATE t_incident_alert, t_incident CASCADE");
        int size = c.path("offsetMicros").size();
        assertTrue(size > 0);
        assertEquals(size, c.path("keys").size());
        assertEquals(size, c.path("expectedAnchors").size());
        var base = OffsetDateTime.parse("2026-01-01T00:00:00Z");
        List<Integer> failed = indices(c.path("failed"));
        List<Integer> preprocessed = indices(c.path("preprocess"));
        for (int i = 0; i < size; i++) {
            String key = "alert-incident-v1:" + String.format("%064x", c.path("keys").get(i).asInt() + 1);
            db.insertAlert(id(i), key, base.plusNanos(c.path("offsetMicros").get(i).asLong() * 1000));
            if (failed.contains(i)) db.sql("UPDATE t_incident_alert SET processing_status='FAILED',next_attempt_at=NULL,"
                    + "last_failure_code='CONTROLLED',failed_at=clock_timestamp() WHERE id='" + id(i) + "'");
        }
        for (int i : preprocessed) assertTrue(db.aggregation.processOne(id(i)));
        if (c.has("sealAtMicros")) {
            assertEquals(1, preprocessed.size());
            var incident = db.incidents.findById(db.alerts.findById(id(preprocessed.get(0))).getIncidentId());
            db.sql("UPDATE t_incident SET lifecycle_state='RESOLVED',aggregation_slot_active=false,lifecycle_sealed_at='"
                    + base.plusNanos(c.path("sealAtMicros").asLong() * 1000) + "' WHERE id='" + incident.getId() + "'");
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) if (!preprocessed.contains(i)) order.add(i);
        Collections.shuffle(order, new Random(seed));
        for (int i : order) assertEquals(!failed.contains(i), db.aggregation.processOne(id(i)));
        Set<String> materializedAnchors = new HashSet<>();
        for (int i = 0; i < size; i++) {
            var alert = db.alerts.findById(id(i));
            assertNotNull(alert, "accepted input must remain queryable");
            JsonNode expected = c.path("expectedAnchors").get(i);
            if (expected.isNull()) {
                assertEquals("FAILED", alert.getProcessingStatus());
                assertNull(alert.getIncidentId());
            } else {
                assertEquals("PROCESSED", alert.getProcessingStatus());
                var incident = db.incidents.findById(alert.getIncidentId());
                assertNotNull(incident);
                assertEquals(id(expected.asInt()), incident.getEpisodeAnchorAlertId());
                materializedAnchors.add(incident.getEpisodeAnchorAlertId());
            }
            assertFalse(db.aggregation.processOne(id(i)), "revisit must not duplicate business facts");
        }
        int processed = size - failed.size();
        assertEquals(size, db.count("t_incident_alert"));
        assertEquals(processed, db.count("t_incident_alert WHERE processing_status='PROCESSED'"));
        assertEquals(failed.size(), db.count("t_incident_alert WHERE processing_status='FAILED'"));
        assertEquals(0, db.count("t_incident_alert WHERE processing_status='RECEIVED'"));
        assertEquals(materializedAnchors.size(), db.count("t_incident"));
        assertEquals(processed, db.count("t_incident_timeline WHERE event_type IN ('ALERT_ATTACHED','LATE_ALERT_ATTACHED_AFTER_SEAL')"));
        assertEquals(materializedAnchors.size(), db.count("t_incident_timeline WHERE event_type='INCIDENT_CREATED'"));
        results.add(Map.of("caseId", c.path("id").asText(), "seed", seed, "inputs", size,
                "processed", processed, "failed", failed.size(), "unfinished", 0,
                "incidents", materializedAnchors.size(), "evidenceLevel", "REAL_PG_SERVICE_CALL_SQL_TIME_FIXTURE"));
    }

    private static String id(int index) { return Integer.toString(10000 + index); }
    private static List<Integer> indices(JsonNode values) {
        List<Integer> result = new ArrayList<>();
        values.forEach(v -> result.add(v.asInt()));
        return result;
    }

    @AfterAll
    static void close() throws Exception {
        try {
            if (db != null) {
                new EvidenceManifestWriter().write("scenario-results.json", results);
                EvidenceManifestWriter.labProof("reliabilityAggregation", Map.of("cases", results,
                        "declaredCases", 15, "orders", 3, "completed", results.size()));
                assertEquals(45, results.size(), "every declared case/order must complete");
            }
        } finally { if (db != null) db.close(); }
    }
}
