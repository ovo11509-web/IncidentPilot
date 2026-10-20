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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer;
import com.nageoffer.ai.ragent.incident.alert.service.AlertFingerprint;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReceiveService;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReplayService;
import com.nageoffer.ai.ragent.incident.alert.worker.AlertRetryPolicy;
import com.nageoffer.ai.ragent.incident.alert.worker.AlertWorker;
import com.nageoffer.ai.ragent.incident.outbox.worker.OutboxRelay;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.junit.jupiter.api.Test;

/** An isolated Spec A path from committed receipt through publish and manual recovery. */
class IncidentAcceptanceSuiteTest {
    @Test
    void acceptedAlertAggregatesTransitionsPublishesAndFailedAlertRecovers() throws Exception {
        try (var db = new IncidentDbFixture()) {
            var canonicalizer = new AlertV1Canonicalizer(db.properties);
            var canonical = canonicalizer.canonicalize(canonicalizer.parseStructure(("""
                    {"externalAlertId":"accept-1","eventType":"FIRING","occurredAt":"2026-09-23T00:00:00Z",
                    "ruleId":"cpu","title":"SAFE_PROBE_TITLE","severity":"HIGH","environment":"prod",
                    "service":"api","resource":{"type":"host","id":"h-1"}}
                    """).getBytes(StandardCharsets.UTF_8)));
            var receive = new AlertReceiveService(db.alerts, db.audit, db.clock, new AlertFingerprint(),
                    db.transactions);
            var submitted = receive.receive("source", canonical, "accept-request");
            assertEquals("RECEIVED", submitted.processingStatus());
            assertTrue(receive.receive("source", canonical, "duplicate-request").duplicate());
            var retry = new AlertRetryPolicy(db.alerts, db.audit, db.incidents, db.properties, db.transactions);
            new AlertWorker(db.alerts, db.aggregation, db.properties, retry).processDue();
            var processed = db.alerts.findById(submitted.alertId());
            assertEquals("PROCESSED", processed.getProcessingStatus());
            var initial = db.incidents.findById(processed.getIncidentId());
            assertEquals(1, initial.getVersion());
            assertEquals(2, db.count("t_incident_outbox WHERE aggregate_version = 1 "
                    + "AND payload_json ->> 'aggregateVersion' = '1'"));
            var ack = db.transitions.transition(initial.getId(), "OPEN", initial.getVersion(),
                    "ACKNOWLEDGED", null, "operator", "ack-request");
            var resolved = db.transitions.transition(initial.getId(), "ACKNOWLEDGED", ack.getVersion(),
                    "RESOLVED", "safe resolution", "operator", "resolve-request");
            assertNotNull(resolved.getLifecycleSealedAt());
            assertEquals(5, db.timeline.findByIncidentId(initial.getId()).size());

            var producer = mock(MessageQueueProducer.class);
            var ok = mock(SendResult.class);
            when(ok.getSendStatus()).thenReturn(SendStatus.SEND_OK);
            when(ok.getMsgId()).thenReturn("mock-broker-id");
            when(producer.send(eq("incident-domain-events"), anyString(), anyString(), any())).thenReturn(ok);
            assertEquals(2, new OutboxRelay(db.outbox, producer, db.incidents, db.properties,
                    db.transactions).publishDue());
            assertEquals(2, db.count("t_incident_outbox WHERE publish_status='PUBLISHED'"));

            db.insertAlert("5002", "alert-incident-v1:" + "f".repeat(64), OffsetDateTime.now().minusSeconds(1));
            for (int attempt = 0; attempt < 5; attempt++) {
                assertEquals(AlertRetryPolicy.Outcome.RETRY_SCHEDULED, retry.afterFailure("5002"));
                db.sql("UPDATE t_incident_alert SET next_attempt_at=clock_timestamp()-interval '1 second' "
                        + "WHERE id='5002'");
            }
            assertEquals(AlertRetryPolicy.Outcome.TERMINAL_FAILED, retry.afterFailure("5002"));
            var failed = db.alerts.findById("5002");
            assertEquals("FAILED", failed.getProcessingStatus());
            var replay = new AlertReplayService(db.alerts, db.audit, db.transactions);
            replay.replay("5002", failed.getVersion(), "reviewed source", "operator", "replay-request");
            new AlertWorker(db.alerts, db.aggregation, db.properties, retry).processDue();
            assertEquals("PROCESSED", db.alerts.findById("5002").getProcessingStatus());
            assertEquals(2, db.count("t_incident"));
            assertEquals(0, db.operations.snapshot().getReceivedBacklogCount());
            assertFalse(db.text("SELECT string_agg(payload_json::text, ' ') FROM t_incident_outbox")
                    .contains("SAFE_PROBE_TITLE"));
        }
    }
}
