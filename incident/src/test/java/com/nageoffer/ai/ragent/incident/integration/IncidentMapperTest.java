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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
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

class IncidentMapperTest {

    @Test
    void versionedTransitionAndAppendOnlyTimelineUseActualPostgres() throws Exception {
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
            }
            var dataSource = new UnpooledDataSource("org.postgresql.Driver", container.getJdbcUrl(),
                    container.getUsername(), container.getPassword());
            var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
            configuration.addMapper(IncidentMapper.class);
            configuration.addMapper(TimelineMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);
            try (var session = factory.openSession(false)) {
                var incidents = session.getMapper(IncidentMapper.class);
                var timeline = session.getMapper(TimelineMapper.class);
                var now = OffsetDateTime.now(ZoneOffset.UTC);
                var incident = new IncidentMapper.NewIncident();
                incident.setId("incident-1");
                incident.setAggregationKey("alert-incident-v1:key");
                incident.setFingerprint("b".repeat(64));
                incident.setAggregationRuleVersion("alert-incident-v1");
                incident.setAggregationWindowSeconds(1800);
                incident.setEpisodeStartAt(now);
                incident.setEpisodeAnchorAlertId("alert-1");
                incident.setFirstAlertReceivedAt(now);
                incident.setLastAlertReceivedAt(now);
                incident.setAlertCount(1);
                incident.setAggregationSlotActive(true);
                incident.setCreatedEventId("event-created");
                assertEquals(1, incidents.insert(incident));
                assertEquals(1, timeline.insert(fact("fact-created", 1, "INCIDENT_CREATED")));
                assertEquals(1, timeline.insert(fact("fact-attached", 2, "ALERT_ATTACHED")));
                assertEquals(1, incidents.transition("incident-1", "OPEN", 1, "ACKNOWLEDGED", null, 1));
                assertEquals(0, incidents.transition("incident-1", "OPEN", 1, "RESOLVED", now, 2));
                assertEquals(1, timeline.insert(fact("fact-transition", 3, "INCIDENT_STATUS_CHANGED")));
                session.commit();
                var saved = incidents.findById("incident-1");
                assertNotNull(saved);
                assertEquals("ACKNOWLEDGED", saved.getLifecycleState());
                assertEquals(2, saved.getVersion());
                assertEquals(3, saved.getTimelineSeq());
                assertEquals(3, timeline.findByIncidentId("incident-1").size());
            }
        }
    }

    private TimelineMapper.Fact fact(String id, long sequence, String type) {
        var fact = new TimelineMapper.Fact();
        fact.setId(id);
        fact.setIncidentId("incident-1");
        fact.setSequenceNo(sequence);
        fact.setBusinessEventKey("incident:incident-1:" + id);
        fact.setEventType(type);
        fact.setActorType("SYSTEM");
        fact.setActorId("test");
        fact.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        fact.setCorrelationId("correlation");
        return fact;
    }
}
