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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard;
import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ActionSchemaTest {
    private static final Path DATABASE = Path.of("..", "resources", "database");
    private static final String ACTION_MARKER = "-- Spec B: approval to safe simulation";

    @Test
    void fullSchemaRetainsOldFactsAndAddsActionConstraints() throws Exception {
        verify(false);
    }

    @Test
    void upgradeRetainsOldFactsAndAddsActionConstraints() throws Exception {
        verify(true);
    }

    private void verify(boolean upgrade) throws Exception {
        var image = DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");
        try (var postgres = new PostgreSQLContainer(image)) {
            postgres.start();
            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = connection.createStatement()) {
                var full = Files.readString(DATABASE.resolve("schema_pg.sql"));
                int marker = full.indexOf(ACTION_MARKER);
                assertTrue(marker > 0);
                statement.execute(upgrade ? full.substring(0, marker) : full);
                if (upgrade) {
                    statement.execute(Files.readString(DATABASE.resolve("upgrades/v2.0.0/260923_incident_safe_action.sql")));
                }
                assertEquals(5, scalar(connection, "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name IN "
                        + "('t_incident_action','t_incident_action_approval','t_incident_action_execution',"
                        + "'t_incident_execution_ticket','t_incident_action_receipt')"));
                assertEquals(1, scalar(connection, "SELECT count(*) FROM pg_indexes "
                        + "WHERE indexname='uk_execution_issued_ticket' AND indexdef LIKE '%WHERE%ISSUED%'"));
                verifyRowsAndConstraints(connection);
                verifyAppendOnly(connection);
                verifyMappers(postgres);
            }
        }
    }

    private void verifyRowsAndConstraints(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO t_incident_alert (id,source_id,external_alert_id,protocol_version,"
                    + "payload_json,payload_digest,event_type,occurred_at,rule_id,title,severity,environment,"
                    + "service,resource_type,resource_id,labels_json,fingerprint,aggregation_key,received_at,"
                    + "correlation_id,processing_status,next_attempt_at,created_at,updated_at) VALUES "
                    + "('alert-1','source','external',1,'{}',repeat('a',64),'FIRING',now(),'rule','title','HIGH',"
                    + "'test','svc','host','h1','{}',repeat('b',64),'alert-incident-v1:key',now(),"
                    + "'correlation','RECEIVED',now(),now(),now())");
            statement.executeUpdate("INSERT INTO t_incident (id,aggregation_key,fingerprint,aggregation_rule_version,"
                    + "aggregation_window_seconds,episode_start_at,episode_anchor_alert_id,first_alert_received_at,"
                    + "last_alert_received_at,alert_count,lifecycle_state,aggregation_slot_active,born_sealed,"
                    + "created_event_id,timeline_seq,version,created_at,updated_at) VALUES "
                    + "('incident-1','alert-incident-v1:key',repeat('b',64),'alert-incident-v1',1800,now(),"
                    + "'alert-1',now(),now(),1,'OPEN',true,false,'created-1',2,0,now(),now())");

            var action = "INSERT INTO t_incident_action (id,incident_id,source_type,source_request_id,"
                    + "proposed_by_type,proposed_by_id,tool_name,tool_version,target_type,target_id,"
                    + "parameters_json,proposal_digest,binding_version,binding_digest,execution_key,state,"
                    + "approval_due_at,created_at,updated_at) VALUES "
                    + "('%s','incident-1','USER','%s','USER','user-1','simulation.set-state','1',"
                    + "'SIM_SERVICE','demo','{\"desiredState\":\"PAUSED\"}',repeat('a',64),"
                    + "'action-binding-v1',repeat('b',64),'%s','%s',now()+interval '15 minutes',now(),now())";
            statement.executeUpdate(action.formatted("action-1", "request-1", "key-1", "PENDING_APPROVAL"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    action.formatted("action-2", "request-1", "key-2", "PENDING_APPROVAL")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    action.formatted("action-2", "request-2", "key-1", "PENDING_APPROVAL")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    action.formatted("action-2", "request-2", "key-2", "NOT_A_STATE")));

            statement.executeUpdate("INSERT INTO t_incident_action_approval "
                    + "(id,action_id,status,binding_digest,approval_due_at,created_at) VALUES "
                    + "('approval-1','action-1','PENDING',repeat('b',64),now()+interval '15 minutes',now())");
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO t_incident_action_approval "
                    + "(id,action_id,status,binding_digest,approval_due_at,created_at) VALUES "
                    + "('approval-2','action-1','PENDING',repeat('b',64),now()+interval '15 minutes',now())"));
            statement.executeUpdate("INSERT INTO t_incident_action_execution "
                    + "(id,action_id,execution_key,dispatch_request_id,tool_name,tool_version,target_type,"
                    + "target_id,binding_digest,max_execution_duration_ms,receipt_grace_period_ms,created_at) "
                    + "VALUES ('execution-1','action-1','key-1','dispatch-1','simulation.set-state','1',"
                    + "'SIM_SERVICE','demo',repeat('b',64),120000,180000,now())");
            var ticket = "INSERT INTO t_incident_execution_ticket "
                    + "(id,execution_id,secret_digest,status,issued_at,expires_at) VALUES "
                    + "('%s','execution-1',repeat('%s',64),'ISSUED',now(),now()+interval '2 minutes')";
            statement.executeUpdate(ticket.formatted("ticket-1", "c"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(ticket.formatted("ticket-2", "d")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "INSERT INTO t_incident_execution_ticket "
                    + "(id,execution_id,secret_digest,status,issued_at,expires_at) VALUES "
                    + "('ticket-bad','missing',repeat('e',64),'ISSUED',now(),now()+interval '2 minutes')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    "UPDATE t_incident_execution_ticket SET status='CONSUMED' WHERE id='ticket-1'"));
            statement.executeUpdate("INSERT INTO t_incident_action_receipt (id,execution_id,action_id,request_id,"
                    + "receipt_version,receipt_digest,result_status,result_code,tool_name,tool_version,"
                    + "target_type,target_id,execution_key,occurred_at,accepted_at,summary_json,disposition) VALUES "
                    + "('receipt-1','execution-1','action-1','dispatch-1',1,repeat('f',64),'UNKNOWN',"
                    + "'TIMEOUT_UNPROVEN','simulation.set-state','1','SIM_SERVICE','demo','key-1',"
                    + "now(),now(),'{}','APPLIED')");
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO t_incident_action_receipt "
                    + "(id,execution_id,action_id,request_id,receipt_version,receipt_digest,result_status,"
                    + "result_code,tool_name,tool_version,target_type,target_id,execution_key,occurred_at,"
                    + "accepted_at,summary_json,disposition) VALUES "
                    + "('receipt-bad','missing','action-1','dispatch-1',1,repeat('e',64),'UNKNOWN',"
                    + "'TIMEOUT_UNPROVEN','simulation.set-state','1','SIM_SERVICE','demo','key-1',"
                    + "now(),now(),'{}','APPLIED')"));

            statement.executeUpdate("INSERT INTO t_incident_timeline (id,incident_id,sequence_no,business_event_key,"
                    + "event_type,actor_type,actor_id,occurred_at,correlation_id,related_entity_type,"
                    + "related_entity_id,summary_json,created_at) VALUES "
                    + "('timeline-old','incident-1',1,'incident:incident-1:created','INCIDENT_CREATED','SYSTEM',"
                    + "'system',now(),'correlation','INCIDENT','incident-1','{}',now()),"
                    + "('timeline-new','incident-1',2,'action:action-1:proposed','ACTION_PROPOSED','USER',"
                    + "'user-1',now(),'correlation','ACTION','action-1','{}',now())");
            statement.executeUpdate("INSERT INTO t_incident_outbox (event_id,business_event_key,incident_id,"
                    + "aggregate_version,event_type,schema_version,correlation_id,occurred_at,payload_json,"
                    + "publish_status,next_attempt_at,created_at,updated_at) VALUES "
                    + "('event-old','incident:incident-1:created','incident-1',0,'incident.created',1,"
                    + "'correlation',now(),'{}','PENDING',now(),now(),now()),"
                    + "('event-new','action:action-1:approval:approval-1','incident-1',0,"
                    + "'incident.action-approval-decided',1,'correlation',now(),'{}','PENDING',now(),now(),now())");
        }
    }

    private void verifyAppendOnly(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("SET ROLE ragent_app");
            try {
                for (var table : new String[] {"t_incident_timeline", "t_incident_action_receipt"}) {
                    assertTrue(privilege(connection, table, "SELECT"));
                    assertTrue(privilege(connection, table, "INSERT"));
                    assertFalse(privilege(connection, table, "UPDATE"));
                    assertFalse(privilege(connection, table, "DELETE"));
                    assertThrows(SQLException.class, () -> statement.executeUpdate(
                            "UPDATE " + table + " SET id=id WHERE FALSE"));
                }
            } finally {
                statement.execute("RESET ROLE");
            }
        }
    }

    private void verifyMappers(PostgreSQLContainer postgres) {
        var dataSource = new UnpooledDataSource("org.postgresql.Driver", postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
        var configuration = new Configuration(new Environment("action-test",
                new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(ActionMapper.class);
        configuration.addMapper(ApprovalMapper.class);
        configuration.addMapper(ExecutionMapper.class);
        configuration.addMapper(TicketMapper.class);
        configuration.addMapper(ReceiptMapper.class);
        configuration.addMapper(IncidentMapper.class);
        configuration.addMapper(AggregationClockMapper.class);
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        var actions = session.getMapper(ActionMapper.class);
        assertEquals("PENDING_APPROVAL", actions.lockById("action-1").getState());
        assertEquals("action-1", actions.findBySource("incident-1", "USER", "request-1").getId());
        assertEquals(1, actions.transition("action-1", "PENDING_APPROVAL", 0,
                "APPROVAL_REJECTED", null, "REJECTED_BY_USER"));
        assertEquals(0, actions.transition("action-1", "PENDING_APPROVAL", 0,
                "APPROVED", OffsetDateTime.now().plusMinutes(5), null));

        var approvals = session.getMapper(ApprovalMapper.class);
        assertEquals("PENDING", approvals.lockByActionId("action-1").getStatus());
        assertEquals(1, approvals.decide("action-1", 0, "REJECTED", "user-2", "admin",
                OffsetDateTime.now(), null, "REJECTED_BY_USER", null));
        assertEquals(0, approvals.decide("action-1", 0, "APPROVED", "user-2", "admin",
                OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(5), null, null));

        assertEquals("execution-1", session.getMapper(ExecutionMapper.class)
                .findByActionId("action-1").getId());
        assertEquals("ISSUED", session.getMapper(TicketMapper.class)
                .lockIssued("execution-1").getStatus());
        assertEquals("UNKNOWN", session.getMapper(ReceiptMapper.class)
                .findById("receipt-1").getResultStatus());
        assertFalse(java.util.Arrays.stream(ReceiptMapper.class.getMethods())
                .anyMatch(method -> method.getName().startsWith("update")
                        || method.getName().startsWith("delete")));
        var guard = new ActionTransactionGuard(session.getMapper(IncidentMapper.class),
                session.getMapper(AggregationClockMapper.class), new DataSourceTransactionManager(dataSource));
        long sequence = guard.inIncident("incident-1", locked -> {
            assertEquals("OPEN", locked.row().getLifecycleState());
            assertTrue(locked.now().isAfter(locked.row().getEpisodeStartAt().minusMinutes(1)));
            return locked.reserveTimeline(1);
        });
        assertEquals(3L, sequence);
        assertEquals(3L, session.getMapper(IncidentMapper.class).findById("incident-1").getTimelineSeq());
    }

    private boolean privilege(Connection connection, String table, String operation) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT has_table_privilege(current_user, '"
                        + table + "', '" + operation + "')")) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private int scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }
}
