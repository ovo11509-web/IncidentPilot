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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nageoffer.ai.ragent.incident.alert.worker.AlertWorker;
import com.nageoffer.ai.ragent.incident.alert.worker.AlertRetryPolicy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class IncidentAggregationTest {
    private static final String KEY = "alert-incident-v1:" + "b".repeat(64);

    @Test
    void oneHundredSameKeyWorkersMaterializeOneEpisodeAndOneFactPerAttachment() throws Exception {
        try (var db = new IncidentDbFixture()) {
            var now = db.incidents.databaseNow().minusSeconds(10);
            for (int i = 0; i < 100; i++) {
                db.insertAlert(Integer.toString(100_000 + i), KEY, now.plusNanos(i * 1000L));
            }
            var start = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(16);
            try {
                var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
                for (int i = 0; i < 100; i++) {
                    String id = Integer.toString(100_000 + i);
                    futures.add(pool.submit(() -> {
                        start.await();
                        return db.aggregation.processOne(id);
                    }));
                }
                start.countDown();
                for (var future : futures) assertTrue(future.get());
            } finally {
                pool.shutdownNow();
            }
            assertEquals(1, db.count("t_incident"));
            assertEquals(100, db.count("t_incident_alert WHERE processing_status = 'PROCESSED'"));
            assertEquals(101, db.count("t_incident_timeline"));
            assertEquals(101, db.count("t_incident_outbox"));
            assertEquals(1, db.count("t_incident WHERE aggregation_slot_active"));
            var incident = db.incidents.findAllByAggregationKey(KEY).get(0);
            assertEquals("100000", incident.getEpisodeAnchorAlertId());
            assertEquals(100, incident.getAlertCount());
            assertEquals(101, incident.getTimelineSeq());
        }
    }

    @Test
    void failedAnchorParticipatesButDoesNotAttachUntilSuccessfullyReplayed() throws Exception {
        try (var db = new IncidentDbFixture()) {
            var t = db.incidents.databaseNow().minusMinutes(5);
            db.insertAlert("10", KEY, t);
            db.insertAlert("20", KEY, t.plusSeconds(1));
            db.sql("UPDATE t_incident_alert SET processing_status = 'FAILED', next_attempt_at = NULL, "
                    + "last_failure_code = 'TEST', failed_at = clock_timestamp() WHERE id = '10'");
            assertFalse(db.aggregation.processOne("10"));
            assertEquals(0, db.count("t_incident"));
            assertTrue(db.aggregation.processOne("20"));
            var incident = db.incidents.findAllByAggregationKey(KEY).get(0);
            assertEquals("10", incident.getEpisodeAnchorAlertId());
            assertEquals(1, incident.getAlertCount());
            assertNull(db.alerts.findById("10").getIncidentId());
            assertNotNull(db.alerts.findById("20").getIncidentId());
            db.sql("UPDATE t_incident_alert SET processing_status = 'RECEIVED', "
                    + "next_attempt_at = clock_timestamp(), failed_at = NULL, last_failure_code = NULL, "
                    + "version = version + 1 WHERE id = '10'");
            assertTrue(db.aggregation.processOne("10"));
            assertEquals(1, db.count("t_incident"));
            assertEquals(2, db.incidents.findAllByAggregationKey(KEY).get(0).getAlertCount());
        }
    }

    @Test
    void allFailedMembersRemainUnmaterializedAndLatePresealMemberReusesSealedIncident() throws Exception {
        try (var db = new IncidentDbFixture()) {
            var t = db.incidents.databaseNow().minusMinutes(5);
            db.insertAlert("10", KEY, t);
            db.insertAlert("20", KEY, t.plusSeconds(1));
            db.sql("UPDATE t_incident_alert SET processing_status = 'FAILED', next_attempt_at = NULL, "
                    + "last_failure_code = 'TEST', failed_at = clock_timestamp() WHERE id IN ('10','20')");
            assertFalse(db.aggregation.processOne("10"));
            assertFalse(db.aggregation.processOne("20"));
            assertEquals(0, db.count("t_incident"));
            db.sql("UPDATE t_incident_alert SET processing_status = 'RECEIVED', "
                    + "next_attempt_at = clock_timestamp(), failed_at = NULL, last_failure_code = NULL "
                    + "WHERE id IN ('10','20')");
            assertTrue(db.aggregation.processOne("10"));
            var created = db.incidents.findAllByAggregationKey(KEY).get(0);
            db.sql("UPDATE t_incident SET lifecycle_state = 'RESOLVED', lifecycle_sealed_at = '"
                    + t.plusSeconds(2) + "', aggregation_slot_active = false WHERE id = '" + created.getId() + "'");
            assertTrue(db.aggregation.processOne("20"));
            assertEquals(created.getId(), db.alerts.findById("20").getIncidentId());
            assertEquals("LATE_ALERT_ATTACHED_AFTER_SEAL",
                    db.timeline.findByIncidentId(created.getId()).get(2).getEventType());
            db.insertAlert("30", KEY, t.plusSeconds(3));
            assertTrue(db.aggregation.processOne("30"));
            assertEquals(2, db.count("t_incident"));
            assertEquals(1, db.count("t_incident WHERE aggregation_slot_active"));
        }
    }

    @Test
    void oldWindowCreatesBornSealedIncidentAndNextEpisodeGetsDistinctIdentity() throws Exception {
        try (var db = new IncidentDbFixture()) {
            OffsetDateTime old = OffsetDateTime.parse("2026-01-01T00:00:00Z");
            db.insertAlert("10", KEY, old);
            db.insertAlert("20", KEY, old.plusMinutes(31));
            assertTrue(db.aggregation.processOne("20"));
            assertTrue(db.aggregation.processOne("10"));
            assertEquals(2, db.count("t_incident"));
            assertEquals(0, db.count("t_incident WHERE aggregation_slot_active"));
            assertTrue(db.incidents.findAllByAggregationKey(KEY).stream().allMatch(row -> row.isBornSealed()));
        }
    }

    @Test
    void databaseWorkerRediscoversCommittedDueAlertsAndDoesNotReprocessThem() throws Exception {
        try (var db = new IncidentDbFixture()) {
            var t = db.incidents.databaseNow().minusSeconds(5);
            db.insertAlert("10", KEY, t);
            db.insertAlert("20", KEY, t.plusSeconds(1));
            var worker = new AlertWorker(db.alerts, db.aggregation, db.properties,
                    new AlertRetryPolicy(db.alerts, db.audit, db.incidents, db.properties, db.transactions));
            worker.processDue();
            assertEquals(2, db.count("t_incident_alert WHERE processing_status = 'PROCESSED'"));
            assertEquals(1, db.count("t_incident"));
            assertEquals(3, db.count("t_incident_timeline"));
            worker.processDue();
            assertEquals(3, db.count("t_incident_timeline"));
        }
    }
}
