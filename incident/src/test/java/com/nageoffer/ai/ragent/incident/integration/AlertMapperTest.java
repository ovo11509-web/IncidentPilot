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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nageoffer.ai.ragent.incident.alert.dao.AlertAuditMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class AlertMapperTest {

    @Test
    void insertReadAndAppendFactUseActualPostgresConstraints() throws Exception {
        try (var container = new PostgreSQLContainer("postgres:16-alpine")) {
            container.start();
            try (var connection = DriverManager.getConnection(
                            container.getJdbcUrl(), container.getUsername(), container.getPassword());
                    var statement = connection.createStatement()) {
                statement.execute(Files.readString(Path.of("..", "resources", "database", "upgrades", "v2.0.0",
                        "260923_incident_core.sql")));
            }
            var dataSource = new UnpooledDataSource("org.postgresql.Driver", container.getJdbcUrl(),
                    container.getUsername(), container.getPassword());
            var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource));
            configuration.addMapper(AlertMapper.class);
            configuration.addMapper(AlertAuditMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);
            try (var session = factory.openSession(false)) {
                var alerts = session.getMapper(AlertMapper.class);
                var audit = session.getMapper(AlertAuditMapper.class);
                var alert = newAlert();
                assertEquals(1, alerts.insert(alert));
                var fact = new AlertAuditMapper.Fact();
                fact.setId("audit-1");
                fact.setAlertId(alert.getId());
                fact.setFactKey("alert:alert-1:received");
                fact.setFactType("ALERT_RECEIVED");
                fact.setActorType("SOURCE");
                fact.setActorId(alert.getSourceId());
                fact.setCorrelationId(alert.getCorrelationId());
                assertEquals(1, audit.insert(fact));
                session.commit();
                var saved = alerts.findByIdentity(alert.getSourceId(), alert.getExternalAlertId());
                assertNotNull(saved);
                assertEquals(alert.getId(), saved.getId());
                assertEquals("RECEIVED", saved.getProcessingStatus());
                assertEquals(1, audit.findByAlertId(alert.getId()).size());
                alert.setId("alert-2");
                assertThrows(PersistenceException.class, () -> alerts.insert(alert));
                session.rollback();
            }
        }
    }

    private AlertMapper.NewAlert newAlert() {
        var alert = new AlertMapper.NewAlert();
        alert.setId("alert-1");
        alert.setSourceId("source");
        alert.setExternalAlertId("external");
        alert.setPayloadJson("{}");
        alert.setPayloadDigest("a".repeat(64));
        alert.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        alert.setRuleId("rule");
        alert.setTitle("title");
        alert.setSeverity("HIGH");
        alert.setEnvironment("prod");
        alert.setService("svc");
        alert.setResourceType("host");
        alert.setResourceId("h1");
        alert.setLabelsJson("{}");
        alert.setFingerprint("b".repeat(64));
        alert.setAggregationKey("alert-incident-v1:key");
        alert.setReceivedAt(OffsetDateTime.now(ZoneOffset.UTC));
        alert.setCorrelationId("correlation");
        return alert;
    }
}
