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

import com.nageoffer.ai.ragent.incident.alert.dao.AlertAuditMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.core.dao.OperationsMapper;
import com.nageoffer.ai.ragent.incident.core.service.EpisodePartitioner;
import com.nageoffer.ai.ragent.incident.core.service.IncidentAggregationService;
import com.nageoffer.ai.ragent.incident.core.service.IncidentStateMachine;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import com.nageoffer.ai.ragent.incident.outbox.dao.OutboxMapper;
import com.nageoffer.ai.ragent.incident.outbox.service.IncidentEventWriter;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import com.nageoffer.ai.ragent.incident.timeline.service.TimelineWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Isolated PostgreSQL fixture for Spec A integration assertions. */
final class IncidentDbFixture implements AutoCloseable {
    private final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    final AlertMapper alerts;
    final AlertAuditMapper audit;
    final IncidentMapper incidents;
    final TimelineMapper timeline;
    final OutboxMapper outbox;
    final AggregationClockMapper clock;
    final OperationsMapper operations;
    final DataSourceTransactionManager transactions;
    final IncidentAggregationService aggregation;
    final IncidentTransitionService transitions;
    final IncidentProperties properties = new IncidentProperties();

    IncidentDbFixture() throws Exception {
        postgres.start();
        sql(Files.readString(Path.of("..", "resources", "database", "upgrades", "v2.0.0",
                "260923_incident_core.sql")));
        var dataSource = new UnpooledDataSource("org.postgresql.Driver", postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
        var configuration = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(AlertMapper.class);
        configuration.addMapper(AlertAuditMapper.class);
        configuration.addMapper(IncidentMapper.class);
        configuration.addMapper(TimelineMapper.class);
        configuration.addMapper(OutboxMapper.class);
        configuration.addMapper(AggregationClockMapper.class);
        configuration.addMapper(OperationsMapper.class);
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        alerts = session.getMapper(AlertMapper.class);
        audit = session.getMapper(AlertAuditMapper.class);
        incidents = session.getMapper(IncidentMapper.class);
        timeline = session.getMapper(TimelineMapper.class);
        outbox = session.getMapper(OutboxMapper.class);
        clock = session.getMapper(AggregationClockMapper.class);
        operations = session.getMapper(OperationsMapper.class);
        transactions = new DataSourceTransactionManager(dataSource);
        aggregation = new IncidentAggregationService(alerts, audit, incidents, clock, new EpisodePartitioner(),
                new TimelineWriter(timeline), new IncidentEventWriter(outbox), properties, transactions);
        transitions = new IncidentTransitionService(incidents, alerts, clock, new IncidentStateMachine(),
                new TimelineWriter(timeline), transactions);
    }

    void insertAlert(String id, String key, OffsetDateTime receivedAt) {
        var alert = new AlertMapper.NewAlert();
        alert.setId(id);
        alert.setSourceId("source");
        alert.setExternalAlertId("external-" + id);
        alert.setPayloadJson("{}");
        alert.setPayloadDigest("a".repeat(64));
        alert.setOccurredAt(receivedAt.plusYears(1));
        alert.setRuleId("cpu");
        alert.setTitle("test");
        alert.setSeverity("HIGH");
        alert.setEnvironment("prod");
        alert.setService("api");
        alert.setResourceType("host");
        alert.setResourceId("h-1");
        alert.setLabelsJson("{}");
        alert.setFingerprint(key.substring("alert-incident-v1:".length()));
        alert.setAggregationKey(key);
        alert.setReceivedAt(receivedAt);
        alert.setCorrelationId("9" + id);
        alerts.insert(alert);
    }

    long count(String table) throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    String text(String query) throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery(query)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    void sql(String command) throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(command);
        }
    }

    @Override
    public void close() {
        postgres.close();
    }
}
