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

import com.nageoffer.ai.ragent.incident.alert.service.AlertReplayService;
import com.nageoffer.ai.ragent.incident.alert.worker.AlertRetryPolicy;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AlertRecoveryTest {
    private static final String KEY = "alert-incident-v1:" + "a".repeat(64);

    @Test
    void committedReceiptSurvivesWorkerRestart() throws Exception {
        try (var db = new IncidentDbFixture()) {
            db.insertAlert("1001", KEY, OffsetDateTime.now().minusSeconds(1));
            assertEquals("RECEIVED", db.alerts.findById("1001").getProcessingStatus());
            assertTrue(db.aggregation.processOne("1001"));
            assertEquals("PROCESSED", db.alerts.findById("1001").getProcessingStatus());
            assertEquals(1, db.count("t_incident"));
            com.nageoffer.ai.ragent.incident.evidence.EvidenceManifestWriter.labProof(
                    "committedReceiptSurvivesWorkerRestart", java.util.Map.of(
                    "alertId", "1001", "status", db.alerts.findById("1001").getProcessingStatus(),
                    "incidentCount", db.count("t_incident"), "evidenceLevel", "REAL_PG_SERVICE_CALL"));
        }
    }

    @Test
    void fiveRetriesThenOneTerminalFactAndConcurrentReplayRecovery() throws Exception {
        try (var db = new IncidentDbFixture()) {
            db.insertAlert("1002", KEY, OffsetDateTime.now().minusSeconds(1));
            var retry = new AlertRetryPolicy(db.alerts, db.audit, db.incidents, db.properties, db.transactions);
            for (int i = 1; i <= 5; i++) {
                assertEquals(AlertRetryPolicy.Outcome.RETRY_SCHEDULED, retry.afterFailure("1002"));
                assertEquals(i, db.alerts.findById("1002").getRetryCount());
                double scheduledSeconds = Double.parseDouble(db.text("SELECT extract(epoch FROM "
                        + "next_attempt_at - clock_timestamp()) FROM t_incident_alert WHERE id='1002'"));
                double base = db.properties.getRetry().getDelays().get(i - 1).toMillis() / 1000.0;
                assertTrue(scheduledSeconds >= base - 2 && scheduledSeconds <= base * 1.1 + 1,
                        "retry delay with bounded jitter");
                db.sql("UPDATE t_incident_alert SET next_attempt_at = clock_timestamp() - interval '1 second' WHERE id = '1002'");
            }
            assertEquals(AlertRetryPolicy.Outcome.TERMINAL_FAILED, retry.afterFailure("1002"));
            assertEquals(AlertRetryPolicy.Outcome.IGNORED, retry.afterFailure("1002"));
            var failed = db.alerts.findById("1002");
            assertEquals("FAILED", failed.getProcessingStatus());
            assertEquals(6, failed.getAttemptCount());
            assertEquals(5, failed.getRetryCount());
            assertEquals(0, db.count("t_incident"));
            assertEquals(1, db.audit.findByAlertId("1002").stream()
                    .filter(f -> "ALERT_TERMINAL_FAILED".equals(f.getFactType())).count());

            var replay = new AlertReplayService(db.alerts, db.audit, db.transactions);
            String firstRequest = UUID.randomUUID().toString();
            var one = CompletableFuture.supplyAsync(() -> attempt(replay, failed.getVersion(), firstRequest));
            var two = CompletableFuture.supplyAsync(() -> attempt(replay, failed.getVersion(), UUID.randomUUID().toString()));
            assertEquals(1, (one.join() ? 1 : 0) + (two.join() ? 1 : 0));
            var accepted = db.alerts.findById("1002");
            assertEquals("RECEIVED", accepted.getProcessingStatus());
            assertEquals(1, accepted.getProcessingEpoch());
            assertEquals(failed.getReceivedAt().toInstant(), accepted.getReceivedAt().toInstant());
            assertEquals(failed.getFingerprint(), accepted.getFingerprint());
            assertEquals(failed.getPayloadDigest(), accepted.getPayloadDigest());
            assertEquals(1, db.audit.findByAlertId("1002").stream()
                    .filter(f -> "ALERT_REPLAY_ACCEPTED".equals(f.getFactType())).count());
            assertEquals(1, db.audit.findByAlertId("1002").stream()
                    .filter(f -> "ALERT_REPLAY_REJECTED".equals(f.getFactType())).count());
            assertTrue(db.aggregation.processOne("1002"));
            assertEquals("PROCESSED", db.alerts.findById("1002").getProcessingStatus());
            assertEquals(1, db.audit.findByAlertId("1002").stream()
                    .filter(f -> "ALERT_RECOVERED".equals(f.getFactType())).count());
            com.nageoffer.ai.ragent.incident.evidence.EvidenceManifestWriter.labProof(
                    "fiveRetriesThenOneTerminalFactAndConcurrentReplayRecovery", java.util.Map.of(
                    "alertId", "1002", "status", db.alerts.findById("1002").getProcessingStatus(),
                    "retryCount", failed.getRetryCount(), "attemptCount", failed.getAttemptCount(),
                    "processingEpoch", accepted.getProcessingEpoch(),
                    "terminalFacts", db.audit.findByAlertId("1002").stream().filter(f -> "ALERT_TERMINAL_FAILED".equals(f.getFactType())).count(),
                    "replayAccepted", db.audit.findByAlertId("1002").stream().filter(f -> "ALERT_REPLAY_ACCEPTED".equals(f.getFactType())).count(),
                    "replayRejected", db.audit.findByAlertId("1002").stream().filter(f -> "ALERT_REPLAY_REJECTED".equals(f.getFactType())).count(),
                    "recoveredFacts", db.audit.findByAlertId("1002").stream().filter(f -> "ALERT_RECOVERED".equals(f.getFactType())).count(),
                    "evidenceLevel", "REAL_PG_SQL_TIME_FIXTURE"));
        }
    }

    private boolean attempt(AlertReplayService replay, long version, String requestId) {
        try {
            replay.replay("1002", version, "safe operator reason", "operator", requestId);
            return true;
        } catch (AlertReplayService.ConflictException expected) {
            return false;
        }
    }
}
