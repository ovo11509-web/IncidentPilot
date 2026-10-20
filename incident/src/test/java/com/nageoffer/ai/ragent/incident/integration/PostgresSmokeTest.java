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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class PostgresSmokeTest {

    @Test
    void isolatedDatabaseCanStartConnectAndStopTwice() throws Exception {
        for (int run = 0; run < 2; run++) {
            PostgreSQLContainer container = new PostgreSQLContainer("postgres:16-alpine");
            try (container) {
                container.start();
                assertTrue(container.isRunning());
                try (var connection = DriverManager.getConnection(
                                container.getJdbcUrl(), container.getUsername(), container.getPassword());
                        var statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE smoke (value INTEGER NOT NULL)");
                    statement.executeUpdate("INSERT INTO smoke (value) VALUES (1)");
                    try (var result = statement.executeQuery("SELECT COUNT(*) FROM smoke")) {
                        assertTrue(result.next());
                        assertEquals(1, result.getInt(1));
                    }
                }
            }
            assertFalse(container.isRunning());
        }
    }
}
