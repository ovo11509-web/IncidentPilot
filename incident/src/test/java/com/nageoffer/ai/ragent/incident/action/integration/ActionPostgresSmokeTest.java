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

package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class ActionPostgresSmokeTest {

    @Test
    void isolatedActionDatabaseStartsFromSpecABaselineAndIsDiscarded() throws Exception {
        for (int run = 0; run < 2; run++) {
            var container = new PostgreSQLContainer("postgres:16-alpine");
            try (container) {
                container.start();
                try (var connection = DriverManager.getConnection(
                                container.getJdbcUrl(), container.getUsername(), container.getPassword());
                        var statement = connection.createStatement()) {
                    statement.execute(Files.readString(Path.of("..", "resources", "database", "upgrades",
                            "v2.0.0", "260923_incident_core.sql")));
                    try (var tables = statement.executeQuery("SELECT count(*) FROM information_schema.tables "
                            + "WHERE table_schema='public' AND table_name IN "
                            + "('t_incident', 't_incident_alert', 't_incident_alert_audit', "
                            + "'t_incident_timeline', 't_incident_outbox')")) {
                        tables.next();
                        assertEquals(5, tables.getInt(1));
                    }
                    statement.execute("CREATE TABLE action_fixture (id INTEGER PRIMARY KEY)");
                    statement.executeUpdate("INSERT INTO action_fixture VALUES (1)");
                }
            }
            assertFalse(container.isRunning());
        }
    }
}
