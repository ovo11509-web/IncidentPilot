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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertAuditMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.service.AlertFingerprint;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReceiveService;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

class AlertIdempotencyTest {
    private static final String FIRST = """
            {"externalAlertId":"e-1","eventType":"FIRING","occurredAt":"2026-09-23T00:00:00Z",
            "ruleId":"cpu","title":"CPU high","severity":"HIGH","environment":"prod",
            "service":"api","resource":{"type":"host","id":"h-1"}}
            """;

    @Test
    void oneHundredConcurrentSubmissionsCommitOneAlertAndOneAuditFact() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            migrate(postgres);
            var fixture = fixture(postgres);
            var canonicalizer = new AlertV1Canonicalizer(new IncidentProperties());
            var canonical = canonicalizer.canonicalize(canonicalizer.parseStructure(FIRST.getBytes(StandardCharsets.UTF_8)));
            var equivalent = canonicalizer.canonicalize(canonicalizer.parseStructure(("""
                    {"resource":{"id":"h-1","type":"host"},"service":"api","environment":"prod",
                    "severity":"HIGH","title":"CPU high","ruleId":"cpu","occurredAt":"2026-09-23T00:00:00Z",
                    "eventType":"FIRING","externalAlertId":"e-1","labels":{}}
                    """).getBytes(StandardCharsets.UTF_8)));
            assertEquals(canonical.payloadDigest(), equivalent.payloadDigest());
            var start = new CountDownLatch(1);
            var results = new ArrayList<java.util.concurrent.Future<AlertReceiveService.Submission>>();
            var pool = Executors.newFixedThreadPool(20);
            try {
                for (int i = 0; i < 100; i++) {
                    results.add(pool.submit(() -> {
                        start.await();
                        return fixture.service().receive("source", canonical, "request");
                    }));
                }
                // A bounded worker pool has only 20 tasks running; release when all are enqueued.
                start.countDown();
                String alertId = null;
                for (var result : results) {
                    var submission = result.get();
                    if (alertId == null) alertId = submission.alertId();
                    assertEquals(alertId, submission.alertId());
                    assertEquals("RECEIVED", submission.processingStatus());
                }
            } finally {
                pool.shutdownNow();
            }
            assertEquals(1, count(postgres, "t_incident_alert"));
            assertEquals(1, count(postgres, "t_incident_alert_audit"));
            assertTrue(fixture.service().receive("source", equivalent, "new-request").duplicate());
            var changed = canonicalizer.canonicalize(canonicalizer.parseStructure(
                    FIRST.replace("CPU high", "CPU critical").getBytes(StandardCharsets.UTF_8)));
            assertThrows(AlertReceiveService.ConflictException.class,
                    () -> fixture.service().receive("source", changed, "conflict"));
            assertEquals(1, count(postgres, "t_incident_alert"));
            assertEquals(1, count(postgres, "t_incident_alert_audit"));
        }
    }

    @Test
    void auditFailureRollsBackAlertAndSafeRetryCanCommit() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            migrate(postgres);
            var fixture = fixture(postgres);
            var brokenAudit = mock(AlertAuditMapper.class);
            when(brokenAudit.insert(any())).thenThrow(new DataAccessResourceFailureException("injected failure"));
            var failing = new AlertReceiveService(fixture.alerts(), brokenAudit, fixture.clock(),
                    new AlertFingerprint(), fixture.transactions());
            var canonicalizer = new AlertV1Canonicalizer(new IncidentProperties());
            var canonical = canonicalizer.canonicalize(canonicalizer.parseStructure(FIRST.getBytes(StandardCharsets.UTF_8)));
            assertThrows(AlertReceiveService.PersistenceUnavailableException.class,
                    () -> failing.receive("source", canonical, "failed-attempt"));
            assertEquals(0, count(postgres, "t_incident_alert"));
            assertEquals(0, count(postgres, "t_incident_alert_audit"));
            assertEquals("RECEIVED", fixture.service().receive("source", canonical, "retry").processingStatus());
            assertEquals(1, count(postgres, "t_incident_alert"));
            assertEquals(1, count(postgres, "t_incident_alert_audit"));
        }
    }

    private void migrate(PostgreSQLContainer postgres) throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("..", "resources", "database", "upgrades", "v2.0.0",
                    "260923_incident_core.sql")));
        }
    }

    private Fixture fixture(PostgreSQLContainer postgres) {
        var dataSource = new UnpooledDataSource("org.postgresql.Driver", postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
        var configuration = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(AlertMapper.class);
        configuration.addMapper(AlertAuditMapper.class);
        configuration.addMapper(AggregationClockMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        var template = new SqlSessionTemplate(factory);
        var alerts = template.getMapper(AlertMapper.class);
        var audit = template.getMapper(AlertAuditMapper.class);
        var clock = template.getMapper(AggregationClockMapper.class);
        var manager = new DataSourceTransactionManager(dataSource);
        return new Fixture(alerts, clock, manager,
                new AlertReceiveService(alerts, audit, clock, new AlertFingerprint(), manager));
    }

    private long count(PostgreSQLContainer postgres, String table) throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private record Fixture(AlertMapper alerts, AggregationClockMapper clock,
            DataSourceTransactionManager transactions, AlertReceiveService service) {}
}
