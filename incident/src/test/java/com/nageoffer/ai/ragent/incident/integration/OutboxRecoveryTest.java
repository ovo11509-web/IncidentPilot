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
import com.nageoffer.ai.ragent.incident.outbox.worker.OutboxRelay;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.junit.jupiter.api.Test;

class OutboxRecoveryTest {
    @Test
    void failedSendAndExpiredClaimReplayTheSameEnvelopeAndKey() throws Exception {
        try (var db = new IncidentDbFixture()) {
            db.insertAlert("2001", "alert-incident-v1:" + "b".repeat(64), OffsetDateTime.now().minusSeconds(1));
            assertTrue(db.aggregation.processOne("2001"));
            assertThrows(Exception.class, () -> db.sql("UPDATE t_incident_outbox SET schema_version = 2"));
            assertEquals(2, db.count("t_incident_outbox WHERE schema_version = 1"));
            var producer = mock(MessageQueueProducer.class);
            var captured = new ArrayList<String>();
            when(producer.send(eq("incident-domain-events"), anyString(), anyString(), any())).thenAnswer(call -> {
                assertInstanceOf(java.util.Map.class, call.getArgument(3));
                @SuppressWarnings("unchecked")
                var envelope = (java.util.Map<String, Object>) call.getArgument(3);
                assertEquals(call.getArgument(1), envelope.get("eventId"));
                assertEquals(1, envelope.get("schemaVersion"));
                captured.add(call.getArgument(1) + ":" + envelope);
                if (captured.size() <= 2) throw new IllegalStateException("simulated transport failure");
                var ok = mock(SendResult.class);
                when(ok.getSendStatus()).thenReturn(SendStatus.SEND_OK);
                when(ok.getMsgId()).thenReturn("broker-id");
                return ok;
            });
            var relay = new OutboxRelay(db.outbox, producer, db.incidents, db.properties, db.transactions);
            assertEquals(0, relay.publishDue());
            assertEquals(2, db.count("t_incident_outbox WHERE publish_status = 'PENDING'"));
            assertTrue(Double.parseDouble(db.text("SELECT max(extract(epoch FROM "
                    + "next_attempt_at - clock_timestamp())) FROM t_incident_outbox")) <= 900);
            db.sql("UPDATE t_incident_outbox SET next_attempt_at = clock_timestamp() - interval '1 second'");
            assertEquals(2, relay.publishDue());
            assertEquals(2, db.count("t_incident_outbox WHERE publish_status = 'PUBLISHED'"));
            assertEquals(captured.get(0), captured.get(2));
            assertEquals(captured.get(1), captured.get(3));

            // A successful broker send followed by a failed mark can be delivered again.
            db.sql("UPDATE t_incident_outbox SET publish_status = 'PENDING', next_attempt_at = clock_timestamp(), "
                    + "claim_token = NULL, claim_until = NULL, published_at = NULL, broker_message_id = NULL "
                    + "WHERE event_type = 'incident.created'");
            db.sql("CREATE OR REPLACE FUNCTION fail_publish_mark() RETURNS trigger LANGUAGE plpgsql AS $$ "
                    + "BEGIN IF NEW.publish_status = 'PUBLISHED' THEN RAISE EXCEPTION 'injected'; END IF; "
                    + "RETURN NEW; END $$");
            db.sql("CREATE TRIGGER fail_publish_mark BEFORE UPDATE ON t_incident_outbox "
                    + "FOR EACH ROW EXECUTE FUNCTION fail_publish_mark()");
            assertEquals(0, relay.publishDue());
            assertEquals(1, db.count("t_incident_outbox WHERE publish_status = 'PENDING'"));
            db.sql("DROP TRIGGER fail_publish_mark ON t_incident_outbox");
            db.sql("UPDATE t_incident_outbox SET next_attempt_at = clock_timestamp() - interval '1 second' "
                    + "WHERE publish_status = 'PENDING'");
            assertEquals(1, relay.publishDue());
            assertEquals(captured.get(4), captured.get(5));
            assertEquals(2, db.count("t_incident_outbox WHERE publish_status = 'PUBLISHED'"));

            db.sql("UPDATE t_incident_outbox SET publish_status = 'PENDING', next_attempt_at = clock_timestamp(), "
                    + "claim_token = 'expired', claim_until = clock_timestamp() - interval '1 second', "
                    + "published_at = NULL, broker_message_id = NULL "
                    + "WHERE event_type = 'incident.created'");
            assertEquals(1, relay.publishDue());
            assertEquals(captured.get(5), captured.get(6));
            com.nageoffer.ai.ragent.incident.evidence.EvidenceManifestWriter.labProof(
                    "failedSendAndExpiredClaimReplayTheSameEnvelopeAndKey", java.util.Map.of(
                    "eventIds", db.text("SELECT string_agg(event_id, ',' ORDER BY event_id) FROM t_incident_outbox"),
                    "publishedCount", db.count("t_incident_outbox WHERE publish_status = 'PUBLISHED'"),
                    "sendAttempts", captured.size(), "sameEnvelope", captured.get(5).equals(captured.get(6)),
                    "evidenceLevel", "REAL_PG_RELAY_FIXED_PRODUCER", "brokerValidated", false));
        }
    }
}
