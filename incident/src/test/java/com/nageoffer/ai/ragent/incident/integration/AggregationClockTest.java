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

import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class AggregationClockTest {

    private static final String KEY = "alert-incident-v1:key";

    @Test
    void sameKeyConcurrentReceiptsAndSealRemainMonotonic() throws Exception {
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
            configuration.addMapper(AggregationClockMapper.class);
            configuration.addMapper(AlertMapper.class);
            configuration.addMapper(IncidentMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);

            // A future persisted fact simulates a wall-clock rollback without changing the host clock.
            var future = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            try (var session = factory.openSession(false)) {
                session.getMapper(AggregationClockMapper.class).lockKey(KEY);
                assertEquals(1, session.getMapper(AlertMapper.class).insert(alert("alert-0", future, KEY)));
                session.commit();
            }
            OffsetDateTime latestReceived;
            var pool = Executors.newFixedThreadPool(8);
            try {
                var jobs = new ArrayList<Callable<OffsetDateTime>>();
                for (int i = 1; i <= 20; i++) {
                    final int index = i;
                    jobs.add(() -> {
                        try (var session = factory.openSession(false)) {
                            var allocated = session.getMapper(AggregationClockMapper.class).allocateReceivedAt(KEY);
                            session.getMapper(AlertMapper.class).insert(alert("alert-" + index, allocated, KEY));
                            session.commit();
                            return allocated;
                        }
                    });
                }
                var results = pool.invokeAll(jobs);
                var times = new HashSet<OffsetDateTime>();
                for (var result : results) {
                    var receivedAt = result.get();
                    assertTrue(receivedAt.isAfter(future));
                    assertTrue(times.add(receivedAt), "same-key receipts must have distinct times");
                }
                assertEquals(20, times.size());
                latestReceived = times.stream().max(OffsetDateTime::compareTo).orElseThrow();
            } finally {
                pool.shutdown();
                assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
            }

            OffsetDateTime sealedAt;
            try (var session = factory.openSession(false)) {
                var clock = session.getMapper(AggregationClockMapper.class);
                clock.lockKey(KEY);
                var incident = new IncidentMapper.NewIncident();
                incident.setId("incident-1");
                incident.setAggregationKey(KEY);
                incident.setFingerprint("b".repeat(64));
                incident.setAggregationRuleVersion("alert-incident-v1");
                incident.setAggregationWindowSeconds(1800);
                incident.setEpisodeStartAt(future);
                incident.setEpisodeAnchorAlertId("alert-0");
                incident.setFirstAlertReceivedAt(future);
                incident.setLastAlertReceivedAt(latestReceived);
                incident.setAlertCount(1);
                incident.setAggregationSlotActive(true);
                incident.setCreatedEventId("created-1");
                assertEquals(1, session.getMapper(IncidentMapper.class).insert(incident));
                sealedAt = clock.allocateSealedAt(KEY);
                assertEquals(1, session.getMapper(IncidentMapper.class).transition(
                        "incident-1", "OPEN", 1, "RESOLVED", sealedAt, 2));
                var lastReceivedAt = clock.nextReceivedAt(KEY);
                assertTrue(sealedAt.isAfter(future));
                assertTrue(lastReceivedAt.isAfter(sealedAt));
                // Different aggregation keys have independent transaction locks.
                try (var connection = DriverManager.getConnection(
                                container.getJdbcUrl(), container.getUsername(), container.getPassword());
                        var statement = connection.createStatement();
                        var result = statement.executeQuery(
                                "SELECT pg_try_advisory_xact_lock(hashtextextended('alert-incident-v1:other', 0))")) {
                    assertTrue(result.next());
                    assertTrue(result.getBoolean(1));
                }
                session.commit();
            }
            try (var session = factory.openSession(false)) {
                var next = session.getMapper(AggregationClockMapper.class).allocateReceivedAt(KEY);
                assertTrue(next.isAfter(sealedAt));
                session.rollback();
            }
        }
    }

    @Test
    void receiverWaitsForFirstSealAndOverflowFailsClosed() throws Exception {
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
            configuration.addMapper(AggregationClockMapper.class);
            configuration.addMapper(AlertMapper.class);
            configuration.addMapper(IncidentMapper.class);
            var factory = new SqlSessionFactoryBuilder().build(configuration);
            var future = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
            try (var session = factory.openSession(false)) {
                session.getMapper(AggregationClockMapper.class).lockKey(KEY);
                session.getMapper(AlertMapper.class).insert(alert("anchor", future, KEY));
                var incident = new IncidentMapper.NewIncident();
                incident.setId("incident-seal");
                incident.setAggregationKey(KEY);
                incident.setFingerprint("b".repeat(64));
                incident.setAggregationRuleVersion("alert-incident-v1");
                incident.setAggregationWindowSeconds(1800);
                incident.setEpisodeStartAt(future);
                incident.setEpisodeAnchorAlertId("anchor");
                incident.setFirstAlertReceivedAt(future);
                incident.setLastAlertReceivedAt(future);
                incident.setAlertCount(1);
                incident.setAggregationSlotActive(true);
                incident.setCreatedEventId("event-seal");
                session.getMapper(IncidentMapper.class).insert(incident);
                session.commit();
            }
            var pool = Executors.newSingleThreadExecutor();
            try (var sealing = factory.openSession(false)) {
                var sealedAt = sealing.getMapper(AggregationClockMapper.class).allocateSealedAt(KEY);
                sealing.getMapper(IncidentMapper.class).transition("incident-seal", "OPEN", 1, "RESOLVED", sealedAt, 2);
                var started = new CountDownLatch(1);
                var receipt = pool.submit(() -> {
                    try (var session = factory.openSession(false)) {
                        started.countDown();
                        var receivedAt = session.getMapper(AggregationClockMapper.class).allocateReceivedAt(KEY);
                        session.getMapper(AlertMapper.class).insert(alert("after-seal", receivedAt, KEY));
                        session.commit();
                        return receivedAt;
                    }
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> receipt.get(200, TimeUnit.MILLISECONDS));
                sealing.commit();
                assertTrue(receipt.get(10, TimeUnit.SECONDS).isAfter(sealedAt));
            } finally {
                pool.shutdown();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }

            String exhaustedKey = "alert-incident-v1:exhausted";
            try (var session = factory.openSession(false)) {
                session.getMapper(AlertMapper.class).insert(alert("overflow", future, exhaustedKey));
                session.commit();
            }
            try (var connection = DriverManager.getConnection(
                            container.getJdbcUrl(), container.getUsername(), container.getPassword());
                    var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE t_incident_alert SET received_at = 'infinity' WHERE id = 'overflow'");
            }
            try (var session = factory.openSession(false)) {
                assertThrows(IllegalStateException.class,
                        () -> session.getMapper(AggregationClockMapper.class).allocateReceivedAt(exhaustedKey));
                session.rollback();
            }
        }
    }

    private AlertMapper.NewAlert alert(String id, OffsetDateTime receivedAt, String key) {
        var alert = new AlertMapper.NewAlert();
        alert.setId(id);
        alert.setSourceId("source");
        alert.setExternalAlertId(id);
        alert.setPayloadJson("{}");
        alert.setPayloadDigest("a".repeat(64));
        alert.setOccurredAt(receivedAt);
        alert.setRuleId("rule");
        alert.setTitle("title");
        alert.setSeverity("HIGH");
        alert.setEnvironment("prod");
        alert.setService("svc");
        alert.setResourceType("host");
        alert.setResourceId("h1");
        alert.setLabelsJson("{}");
        alert.setFingerprint("b".repeat(64));
        alert.setAggregationKey(key);
        alert.setReceivedAt(receivedAt);
        alert.setCorrelationId("correlation");
        return alert;
    }
}
