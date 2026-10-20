/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.incident.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.nageoffer.ai.ragent.incident.evidence.EvidenceManifestWriter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class IncidentEvidenceCatalogTest {
    @Test
    void isolatedRunWritesTruthfulCountsEventIdsAndForbiddenContentScan() throws Exception {
        Instant started = Instant.now();
        var writer = new EvidenceManifestWriter();
        try (var db = new IncidentDbFixture()) {
            var logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            var captured = new ListAppender<ILoggingEvent>();
            captured.start();
            logger.addAppender(captured);
            try {
            db.insertAlert("4001", "alert-incident-v1:" + "e".repeat(64), OffsetDateTime.now().minusSeconds(1));
            db.sql("UPDATE t_incident_alert SET title='SECRET_PROBE', summary='SECRET_PROBE', "
                    + "payload_json='{\"probe\":\"SECRET_PROBE\"}'::jsonb, "
                    + "labels_json='{\"probe\":\"SECRET_PROBE\"}'::jsonb WHERE id='4001'");
            assertTrue(db.aggregation.processOne("4001"));
            String incidentId = db.alerts.findById("4001").getIncidentId();
            var timeline = db.timeline.findByIncidentId(incidentId);
            var operations = db.operations.snapshot();
            String timelineText = db.text("SELECT string_agg(summary_json::text, ' ') FROM t_incident_timeline");
            String auditText = db.text("SELECT string_agg(metadata_json::text, ' ') FROM t_incident_alert_audit");
            String outboxText = db.text("SELECT string_agg(payload_json::text, ' ') FROM t_incident_outbox");
            long hitCount = java.util.stream.Stream.concat(java.util.stream.Stream.of(timelineText, auditText,
                            outboxText, operations.toString(), db.operations.recentFailures().toString()),
                    captured.list.stream().map(ILoggingEvent::getFormattedMessage))
                    .filter(value -> value != null && value.contains("SECRET_PROBE")).count();
            assertEquals(0, hitCount);
            assertEquals(2, timeline.size());
            assertEquals(2, db.count("t_incident_outbox"));
            String firstEvent = db.text("SELECT event_id FROM t_incident_outbox ORDER BY event_type LIMIT 1");
            String secondEvent = db.text("SELECT event_id FROM t_incident_outbox ORDER BY event_type DESC LIMIT 1");
            writer.eventCapture(Map.of("eventId", firstEvent, "eventType", "incident.alert-attached"));
            writer.eventCapture(Map.of("eventId", secondEvent, "eventType", "incident.created"));
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("alerts", db.count("t_incident_alert"));
            counts.put("incidents", db.count("t_incident"));
            counts.put("timeline", db.count("t_incident_timeline"));
            counts.put("alertAudit", db.count("t_incident_alert_audit"));
            counts.put("outbox", db.count("t_incident_outbox"));
            counts.put("receivedBacklog", operations.getReceivedBacklogCount());
            counts.put("pendingOutbox", operations.getPendingOutboxCount());
            writer.write("persistence-counts.json", counts);
            writer.write("content-scan.json", Map.of("probe", "SECRET_PROBE", "hitCount", hitCount,
                    "surfaces", List.of("timeline.summary_json", "alert_audit.metadata_json",
                    "outbox.payload_json", "operations snapshot", "captured logs"),
                    "capturedLogCount", captured.list.size()));
            writer.write("scenario-results.json", List.of(
                    Map.of("scenarioId", "AGG-EVIDENCE-001", "status", "PASSED", "incidentId", incidentId,
                            "expectedTimeline", 2, "actualTimeline", timeline.size()),
                    Map.of("scenarioId", "OUT-EVIDENCE-001", "status", "PASSED", "eventCount", 2),
                    Map.of("scenarioId", "SAFE-EVIDENCE-001", "status", "PASSED", "forbiddenHits", 0)));
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("catalogVersion", "spec-a-v1");
            manifest.put("runId", writer.directory().getFileName().toString());
            manifest.put("gitState", "uncommitted");
            manifest.put("startedAt", started.toString());
            manifest.put("finishedAt", Instant.now().toString());
            manifest.put("jdkVersion", System.getProperty("java.version"));
            manifest.put("postgresImage", "postgres:16-alpine");
            manifest.put("webhookBodyLimitBytes", db.properties.getIngress().getMaxBodyBytes());
            manifest.put("aggregationWindowSeconds", db.properties.getAggregation().getWindow().toSeconds());
            manifest.put("retryDelays", db.properties.getRetry().getDelays().stream().map(Object::toString).toList());
            manifest.put("entry", "IncidentEvidenceCatalogTest via Maven Surefire");
            manifest.put("scope", "isolated evidence smoke; other catalog tests reported by acceptance suite");
            writer.write("manifest.json", manifest);
            } finally {
                logger.detachAppender(captured);
                captured.stop();
            }
        }
        assertTrue(Files.exists(writer.directory().resolve("manifest.json")));
        assertTrue(Files.exists(writer.directory().resolve("event-captures.jsonl")));
    }
}
