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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nageoffer.ai.ragent.incident.outbox.dao.OutboxMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class OutboxMapperTest {

    @Test
    void claimExpiryAndTokenGuardPreserveSinglePublishedResult() throws Exception {
        try (var container = new PostgreSQLContainer("postgres:16-alpine")) {
            container.start();
            try (var connection = DriverManager.getConnection(
                            container.getJdbcUrl(), container.getUsername(), container.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute(Files.readString(Path.of("..", "resources", "database", "upgrades", "v2.0.0",
                        "260923_incident_core.sql")));
                statement.executeUpdate("""
                        INSERT INTO t_incident_alert (id, source_id, external_alert_id, protocol_version,
                            payload_json, payload_digest, event_type, occurred_at, rule_id, title, severity,
                            environment, service, resource_type, resource_id, labels_json, fingerprint,
                            aggregation_key, received_at, correlation_id, processing_status, next_attempt_at,
                            created_at, updated_at)
                        VALUES ('alert-1', 'source', 'external', 1, '{}', repeat('a',64), 'FIRING', now(),
                            'rule', 'title', 'HIGH', 'prod', 'svc', 'host', 'h1', '{}', repeat('b',64),
                            'alert-incident-v1:key', now(), 'correlation', 'RECEIVED', now(), now(), now())
                        """);
                statement.executeUpdate("""
                        INSERT INTO t_incident (id, aggregation_key, fingerprint, aggregation_rule_version,
                            aggregation_window_seconds, episode_start_at, episode_anchor_alert_id,
                            first_alert_received_at, last_alert_received_at, alert_count, lifecycle_state,
                            aggregation_slot_active, born_sealed, created_event_id, timeline_seq, version,
                            created_at, updated_at)
                        VALUES ('incident-1', 'alert-incident-v1:key', repeat('b',64), 'alert-incident-v1',
                            1800, now(), 'alert-1', now(), now(), 1, 'OPEN', true, false,
                            'event-created', 2, 0, now(), now())
                        """);
            }
            var dataSource = new UnpooledDataSource("org.postgresql.Driver", container.getJdbcUrl(),
                    container.getUsername(), container.getPassword());
            var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
            configuration.addMapper(OutboxMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);
            try (var session = factory.openSession(false)) {
                var outbox = session.getMapper(OutboxMapper.class);
                var event = new OutboxMapper.NewEvent();
                event.setEventId("event-1");
                event.setBusinessEventKey("incident:incident-1:created");
                event.setIncidentId("incident-1");
                event.setEventType("incident.created");
                event.setCorrelationId("correlation");
                event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
                event.setPayloadJson("{}");
                assertEquals(1, outbox.insert(event));
                session.commit();

                assertEquals(1, outbox.claimDue("claim-old", 1, 10));
                session.commit();
                assertEquals(1, outbox.findClaimed("claim-old").size());
                assertEquals(0, outbox.claimDue("claim-concurrent", 60, 10));
                session.commit();
                try (var connection = DriverManager.getConnection(
                                container.getJdbcUrl(), container.getUsername(), container.getPassword());
                        var statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE t_incident_outbox SET claim_until = clock_timestamp() - interval '1 second'");
                }
                assertEquals(1, outbox.claimDue("claim-new", 60, 10));
                session.commit();
                assertEquals(0, outbox.markPublished("event-1", "claim-old", "broker-old"));
                assertEquals(1, outbox.markPublished("event-1", "claim-new", "broker-new"));
                session.commit();
                assertEquals("PUBLISHED", outbox.findById("event-1").getPublishStatus());
                assertEquals(2, outbox.findById("event-1").getAttemptCount());
                assertNull(outbox.findById("event-1").getClaimToken());
            }
        }
    }
}
