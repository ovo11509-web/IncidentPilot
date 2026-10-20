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

package com.nageoffer.ai.ragent.mcp.simulation;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Disposable PostgreSQL target for protected simulation tests; never uses application datasource settings. */
final class SimulationTestFixture implements AutoCloseable {
    static final String TARGET_ID = "spec-b-demo-service";
    static final String INITIAL_STATE = "READY";

    private final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    SimulationTestFixture() throws Exception {
        postgres.start();
        try (var connection = connection(); var statement = connection.createStatement()) {
            try (var source = SimulationTestFixture.class.getClassLoader()
                    .getResourceAsStream("db/bit-schema.sql")) {
                if (source == null) throw new IllegalStateException("bit-schema.sql missing");
                statement.execute(new String(source.readAllBytes(), StandardCharsets.UTF_8));
            }
            statement.executeUpdate("INSERT INTO simulation_target (target_id, state) VALUES ('"
                    + TARGET_ID + "', '" + INITIAL_STATE + "')");
        }
    }

    java.sql.Connection connection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    String jdbcUrl() { return postgres.getJdbcUrl(); }
    String username() { return postgres.getUsername(); }
    String password() { return postgres.getPassword(); }

    javax.sql.DataSource dataSource() {
        return new org.springframework.jdbc.datasource.DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    String state() throws SQLException {
        try (var connection = connection();
                var statement = connection.prepareStatement("SELECT state FROM simulation_target WHERE target_id=?")) {
            statement.setString(1, TARGET_ID);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("isolated target missing");
                }
                return result.getString(1);
            }
        }
    }

    long applicationCount() throws SQLException {
        try (var connection = connection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM simulation_application")) {
            result.next();
            return result.getLong(1);
        }
    }

    @Override
    public void close() {
        postgres.close();
    }
}
