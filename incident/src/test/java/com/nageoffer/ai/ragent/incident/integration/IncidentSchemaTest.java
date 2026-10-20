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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class IncidentSchemaTest {

    private static final Path DATABASE = Path.of("..", "resources", "database");
    private static final String INCIDENT_MARKER = "-- Spec A: reliable Alert to Incident";

    @Test
    void fullSchemaCreatesIncidentTablesAndAppendOnlyRole() throws Exception {
        verifySchema(false);
    }

    @Test
    void upgradeCreatesSameIncidentTablesAndAppendOnlyRole() throws Exception {
        verifySchema(true);
    }

    private void verifySchema(boolean upgrade) throws Exception {
        var image = DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");
        try (var container = new PostgreSQLContainer(image)) {
            container.start();
            try (var connection = DriverManager.getConnection(
                    container.getJdbcUrl(), container.getUsername(), container.getPassword())) {
                String fullSchema = Files.readString(DATABASE.resolve("schema_pg.sql"));
                int marker = fullSchema.indexOf(INCIDENT_MARKER);
                assertTrue(marker > 0, "Spec A schema marker must exist");
                try (var statement = connection.createStatement()) {
                    statement.execute(upgrade ? fullSchema.substring(0, marker) : fullSchema);
                    if (upgrade) {
                        statement.execute(Files.readString(DATABASE.resolve("upgrades/v2.0.0/260923_incident_core.sql")));
                    }
                }
                assertEquals(5, scalar(connection, "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name IN "
                        + "('t_incident', 't_incident_alert', 't_incident_alert_audit', "
                        + "'t_incident_timeline', 't_incident_outbox')"));
                assertTrue(scalar(connection, "SELECT count(*) FROM pg_constraint WHERE conrelid IN "
                        + "('t_incident'::regclass, 't_incident_alert'::regclass, "
                        + "'t_incident_alert_audit'::regclass, 't_incident_timeline'::regclass, "
                        + "'t_incident_outbox'::regclass) AND contype IN ('c','f','u')") >= 20);
                assertEquals(1, scalar(connection, "SELECT count(*) FROM pg_indexes WHERE indexname='uk_incident_active_slot' "
                        + "AND indexdef LIKE '%WHERE aggregation_slot_active%'"));
                verifyDatabaseConstraints(connection);
                verifyAppendOnlyRole(connection, "t_incident_alert_audit");
                verifyAppendOnlyRole(connection, "t_incident_timeline");
            }
        }
    }

    private void verifyDatabaseConstraints(Connection connection) throws SQLException {
        String alert = "INSERT INTO t_incident_alert "
                + "(id, source_id, external_alert_id, protocol_version, payload_json, payload_digest, event_type, "
                + "occurred_at, rule_id, title, severity, environment, service, resource_type, resource_id, "
                + "labels_json, fingerprint, aggregation_key, received_at, correlation_id, processing_status, "
                + "next_attempt_at, created_at, updated_at) VALUES "
                + "('%s', 'source', 'external', 1, '{}', repeat('a', 64), 'FIRING', now(), 'rule', 'title', "
                + "'HIGH', 'prod', 'svc', 'host', 'h1', '{}', repeat('b', 64), 'alert-incident-v1:key', "
                + "now(), 'correlation', '%s', now(), now(), now())";
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(alert.formatted("alert-1", "RECEIVED"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(alert.formatted("alert-2", "RECEIVED")),
                    "source and external ID must be unique");
            assertThrows(SQLException.class, () -> statement.executeUpdate(alert.formatted("alert-3", "FAILED")),
                    "FAILED requires a failure code and timestamp");
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO t_incident_alert_audit "
                    + "(id, alert_id, fact_key, fact_type, occurred_at, actor_type, actor_id, correlation_id, "
                    + "processing_epoch, created_at) VALUES "
                    + "('audit-1', 'missing', 'missing:received', 'ALERT_RECEIVED', now(), 'SOURCE', 'source', "
                    + "'correlation', 0, now())"), "audit must reference an existing Alert");

            String incident = "INSERT INTO t_incident "
                    + "(id, aggregation_key, fingerprint, aggregation_rule_version, aggregation_window_seconds, "
                    + "episode_start_at, episode_anchor_alert_id, first_alert_received_at, last_alert_received_at, "
                    + "alert_count, lifecycle_state, aggregation_slot_active, born_sealed, created_event_id, "
                    + "timeline_seq, version, created_at, updated_at) VALUES "
                    + "('%s', 'alert-incident-v1:key', repeat('b', 64), 'alert-incident-v1', %d, "
                    + "now() + interval '%d second', 'alert-1', now(), now(), 1, 'OPEN', true, false, '%s', "
                    + "2, 0, now(), now())";
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(incident.formatted("incident-bad", 0, 0, "event-bad")),
                    "aggregation window must be positive");
            statement.executeUpdate(incident.formatted("incident-1", 1800, 0, "event-1"));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(incident.formatted("incident-2", 1800, 1, "event-2")),
                    "only one active aggregation slot is allowed per key");
        }
    }

    private void verifyAppendOnlyRole(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("SET ROLE ragent_app");
            try {
                assertFalse(privilege(statement, table, "UPDATE"));
                assertFalse(privilege(statement, table, "DELETE"));
                assertTrue(privilege(statement, table, "INSERT"));
                assertTrue(privilege(statement, table, "SELECT"));
                assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE " + table + " SET id=id WHERE FALSE"));
                assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM " + table + " WHERE FALSE"));
            } finally {
                statement.execute("RESET ROLE");
            }
        }
    }

    private boolean privilege(Statement statement, String table, String operation) throws SQLException {
        try (var result = statement.executeQuery("SELECT has_table_privilege(current_user, '" + table + "', '" + operation + "')")) {
            assertTrue(result.next());
            return result.getBoolean(1);
        }
    }

    private int scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }
}
