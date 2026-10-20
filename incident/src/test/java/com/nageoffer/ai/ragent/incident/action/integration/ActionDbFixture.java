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

import com.nageoffer.ai.ragent.incident.action.config.ActionProperties;
import com.nageoffer.ai.ragent.incident.action.contract.ActionBindingV1;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionOutboxWriter;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertAuditMapper;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.core.service.EpisodePartitioner;
import com.nageoffer.ai.ragent.incident.core.service.IncidentAggregationService;
import com.nageoffer.ai.ragent.incident.outbox.service.IncidentEventWriter;
import com.nageoffer.ai.ragent.incident.outbox.dao.OutboxMapper;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import com.nageoffer.ai.ragent.incident.timeline.service.TimelineWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Set;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Fresh Spec A + B PostgreSQL for Action transaction tests. */
final class ActionDbFixture implements AutoCloseable {
    private final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    final ActionMapper actions;
    final ApprovalMapper approvals;
    final ExecutionMapper executions;
    final TicketMapper tickets;
    final ReceiptMapper receipts;
    final IncidentMapper incidents;
    final AlertMapper alerts;
    final AlertAuditMapper audit;
    final AggregationClockMapper clock;
    final TimelineMapper timeline;
    final OutboxMapper outbox;
    final ActionTransactionGuard guard;
    final ActionTimelineWriter actionTimeline;
    final ActionOutboxWriter actionOutbox;
    final TimelineWriter incidentTimeline;
    final ActionProperties properties = new ActionProperties();
    final IncidentProperties incidentProperties = new IncidentProperties();
    final IncidentAggregationService aggregation;
    final DataSourceTransactionManager transactions;

    ActionDbFixture() throws Exception {
        properties.setAllowedSimulationTargets(Set.of("demo"));
        postgres.start();
        sql(Files.readString(Path.of("..", "resources", "database", "upgrades", "v2.0.0",
                "260923_incident_core.sql")));
        sql(Files.readString(Path.of("..", "resources", "database", "upgrades", "v2.0.0",
                "260923_incident_safe_action.sql")));
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
        configuration.addMapper(AlertMapper.class);
        configuration.addMapper(AlertAuditMapper.class);
        configuration.addMapper(AggregationClockMapper.class);
        configuration.addMapper(TimelineMapper.class);
        configuration.addMapper(OutboxMapper.class);
        var session = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration));
        actions = session.getMapper(ActionMapper.class);
        approvals = session.getMapper(ApprovalMapper.class);
        executions = session.getMapper(ExecutionMapper.class);
        tickets = session.getMapper(TicketMapper.class);
        receipts = session.getMapper(ReceiptMapper.class);
        incidents = session.getMapper(IncidentMapper.class);
        alerts = session.getMapper(AlertMapper.class);
        audit = session.getMapper(AlertAuditMapper.class);
        clock = session.getMapper(AggregationClockMapper.class);
        timeline = session.getMapper(TimelineMapper.class);
        outbox = session.getMapper(OutboxMapper.class);
        transactions = new DataSourceTransactionManager(dataSource);
        guard = new ActionTransactionGuard(incidents, clock, transactions);
        actionTimeline = new ActionTimelineWriter(timeline);
        actionOutbox = new ActionOutboxWriter(outbox);
        incidentTimeline = new TimelineWriter(timeline);
        aggregation = new IncidentAggregationService(alerts, audit, incidents, clock,
                new EpisodePartitioner(), incidentTimeline, new IncidentEventWriter(outbox),
                incidentProperties, transactions);
    }

    void seedIncident(String incidentId) throws SQLException {
        sql("INSERT INTO t_incident_alert (id,source_id,external_alert_id,protocol_version,payload_json,"
                + "payload_digest,event_type,occurred_at,rule_id,title,severity,environment,service,"
                + "resource_type,resource_id,labels_json,fingerprint,aggregation_key,received_at,"
                + "correlation_id,processing_status,next_attempt_at,created_at,updated_at) VALUES "
                + "('11','source','external',1,'{}',repeat('a',64),'FIRING',now(),'rule','title','HIGH',"
                + "'test','svc','host','h1','{}',repeat('b',64),'alert-incident-v1:key',now(),"
                + "'99','RECEIVED',now(),now(),now())");
        sql("INSERT INTO t_incident (id,aggregation_key,fingerprint,aggregation_rule_version,"
                + "aggregation_window_seconds,episode_start_at,episode_anchor_alert_id,first_alert_received_at,"
                + "last_alert_received_at,alert_count,lifecycle_state,aggregation_slot_active,born_sealed,"
                + "created_event_id,timeline_seq,version,created_at,updated_at) VALUES "
                + "('" + incidentId + "','alert-incident-v1:key',repeat('b',64),'alert-incident-v1',1800,"
                + "now(),'11',now(),now(),1,'OPEN',true,false,'12',2,0,now(),now())");
    }

    void seedPendingAction(String actionId, OffsetDateTime dueAt) {
        var action = new ActionMapper.NewAction();
        action.setId(actionId);
        action.setIncidentId("100");
        action.setSourceType("USER");
        action.setSourceRequestId("request-" + actionId);
        action.setProposedByType("USER");
        action.setProposedById("42");
        action.setToolName("simulation.set-state");
        action.setToolVersion("1");
        action.setTargetType("SIM_SERVICE");
        action.setTargetId("demo");
        action.setParametersJson("{\"desiredState\":\"PAUSED\"}");
        action.setProposalDigest("a".repeat(64));
        action.setBindingVersion("action-binding-v1");
        action.setExecutionKey("key-" + actionId);
        action.setBindingDigest(ActionBindingV1.bindingDigest(actionId, "100", action.getToolName(),
                action.getToolVersion(), action.getTargetType(), action.getTargetId(),
                action.getParametersJson(), action.getExecutionKey()));
        action.setApprovalDueAt(dueAt);
        actions.insert(action);
        approvals.insertPending("approval-" + actionId, actionId, action.getBindingDigest(), dueAt);
    }

    void sql(String text) throws SQLException {
        try (var connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute(text);
        }
    }

    java.sql.Connection openConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    com.fasterxml.jackson.databind.node.ObjectNode evidenceSnapshot() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var proof = json.createObjectNode().put("observedAt", OffsetDateTime.now().toString());
        var projections = java.util.Map.of(
                "t_incident_action", "id,incident_id,source_request_id,state,version,target_id,parameters_json,execution_key,approval_due_at,authorization_due_at",
                "t_incident_action_approval", "id,action_id,status,decider_id,decided_at,approval_due_at,authorization_due_at",
                "t_incident_action_execution", "id,action_id,execution_key,dispatch_request_id,max_execution_duration_ms,receipt_grace_period_ms,execution_started_at,receipt_due_at,current_receipt_id",
                "t_incident_execution_ticket", "id,execution_id,status,issued_at,expires_at,consumed_at,invalidation_reason",
                "t_incident_action_receipt", "id,action_id,execution_id,result_status,result_code,disposition,summary_json",
                "t_incident_timeline", "id,incident_id,sequence_no,business_event_key,event_type,related_entity_id,summary_json",
                "t_incident_outbox", "event_id,business_event_key,event_type,publish_status,attempt_count,payload_json");
        try (var connection = openConnection(); var statement = connection.createStatement()) {
            for (var projection : projections.entrySet()) {
                try (var rows = statement.executeQuery("SELECT coalesce(jsonb_agg(to_jsonb(p)), '[]'::jsonb) FROM (SELECT "
                        + projection.getValue() + " FROM " + projection.getKey() + ") p")) {
                    rows.next();
                    proof.set(projection.getKey(), json.readTree(rows.getString(1)));
                }
            }
        }
        return proof;
    }

    void saveConvergenceEvidence(String scenario, String ticket) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var proof = evidenceSnapshot().put("scenario", scenario).put("realMcp", false)
                .put("evidenceAndAuthentication", "test substitutes; UNKNOWN state seeded by fixture SQL");
        String content = json.writerWithDefaultPrettyPrinter().writeValueAsString(proof);
        org.junit.jupiter.api.Assertions.assertFalse(content.contains(ticket));
        Path directory = Path.of("target", "b-catalogue-convergence", scenario + "-" + java.util.UUID.randomUUID());
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("summary.json"), content);
    }

    @Override
    public void close() {
        postgres.close();
    }
}
