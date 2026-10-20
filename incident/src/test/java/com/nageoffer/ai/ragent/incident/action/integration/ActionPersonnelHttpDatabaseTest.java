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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionClosureInvalidator;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionProposalService;
import com.nageoffer.ai.ragent.incident.action.web.ActionController;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.core.service.EpisodePartitioner;
import com.nageoffer.ai.ragent.incident.core.service.IncidentStateMachine;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import com.nageoffer.ai.ragent.incident.core.web.IncidentController;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** MVC request mapping with real Action services and disposable PostgreSQL. */
class ActionPersonnelHttpDatabaseTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void rejectionExpiryAndBindingTamperingNeverIssueExecution() throws Exception {
        try (var db = new ActionDbFixture(); var session = Mockito.mockStatic(StpUtil.class)) {
            session.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            var mvc = mvc(db);
            String rejectedId = propose(mvc, "reject-1");
            mvc.perform(post("/incident/v1/actions/{id}/decision", rejectedId)
                            .contentType("application/json")
                            .content("{\"decision\":\"REJECT\",\"reason\":\"unsafe\",\"expectedVersion\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.state").value("APPROVAL_REJECTED"));
            mvc.perform(post("/incident/v1/actions/{id}/decision", rejectedId)
                            .contentType("application/json")
                            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":1}"))
                    .andExpect(status().isConflict());

            String expiredId = propose(mvc, "expire-1");
            db.sql("UPDATE t_incident_action SET approval_due_at=now()-interval '1 second' WHERE id='"
                    + expiredId + "'");
            db.sql("UPDATE t_incident_action_approval SET approval_due_at=now()-interval '1 second' "
                    + "WHERE action_id='" + expiredId + "'");
            mvc.perform(post("/incident/v1/actions/{id}/decision", expiredId)
                            .contentType("application/json")
                            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}"))
                    .andExpect(status().isConflict());
            assertEquals("EXPIRED", db.actions.findById(expiredId).getState());

            String original = proposal("reject-1");
            for (String field : new String[] {"actionId", "incidentId", "executionId", "executionKey",
                    "requestId", "ticket", "bindingVersion"}) {
                mvc.perform(post("/incident/v1/incidents/100/actions")
                                .contentType("application/json")
                                .content(original.replace("\"toolName\"", "\"" + field + "\":\"forged\",\"toolName\"")))
                        .andExpect(status().isUnprocessableEntity());
            }
            for (String body : new String[] {
                    original.replace("simulation.set-state", "other.write"),
                    original.replace("\"toolVersion\":\"1\"", "\"toolVersion\":\"2\""),
                    original.replace("\"targetId\":\"demo\"", "\"targetId\":\"other\"")}) {
                mvc.perform(post("/incident/v1/incidents/100/actions")
                                .contentType("application/json").content(body))
                        .andExpect(status().isUnprocessableEntity());
            }
            mvc.perform(post("/incident/v1/incidents/100/actions")
                            .contentType("application/json")
                            .content(original.replace("PAUSED", "READY")))
                    .andExpect(status().isConflict());
            assertEquals("APPROVAL_REJECTED", db.actions.findById(rejectedId).getState());
            assertEquals(0, count(db, "t_incident_action_execution"));
        }
    }

    @Test
    void closeThroughIncidentMvcInvalidatesUnstartedActionAndLateApproval() throws Exception {
        try (var db = new ActionDbFixture(); var session = Mockito.mockStatic(StpUtil.class)) {
            session.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            var mvc = mvc(db);
            String actionId = propose(mvc, "close-1");
            db.sql("UPDATE t_incident SET lifecycle_state='RESOLVED', "
                    + "lifecycle_sealed_at=clock_timestamp(), aggregation_slot_active=false WHERE id='100'");
            mvc.perform(post("/incident/v1/incidents/100/transitions")
                            .contentType("application/json")
                            .content("{\"expectedState\":\"RESOLVED\",\"expectedVersion\":0,"
                                    + "\"targetState\":\"CLOSED\",\"reason\":\"resolved\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.lifecycleState").value("CLOSED"));
            mvc.perform(post("/incident/v1/actions/{id}/decision", actionId)
                            .contentType("application/json")
                            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}"))
                    .andExpect(status().isConflict());
            assertEquals("EXPIRED", db.actions.findById(actionId).getState());
            assertEquals(0, count(db, "t_incident_action_execution"));
        }
        try (var db = new ActionDbFixture(); var session = Mockito.mockStatic(StpUtil.class)) {
            session.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            var mvc = mvc(db);
            String actionId = propose(mvc, "approve-before-close");
            mvc.perform(post("/incident/v1/actions/{id}/decision", actionId)
                            .contentType("application/json")
                            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.state").value("APPROVED"));
            db.sql("UPDATE t_incident SET lifecycle_state='RESOLVED', "
                    + "lifecycle_sealed_at=clock_timestamp(), aggregation_slot_active=false WHERE id='100'");
            mvc.perform(post("/incident/v1/incidents/100/transitions")
                            .contentType("application/json")
                            .content("{\"expectedState\":\"RESOLVED\",\"expectedVersion\":0,"
                                    + "\"targetState\":\"CLOSED\",\"reason\":\"resolved\"}"))
                    .andExpect(status().isOk());
            assertEquals("EXPIRED", db.actions.findById(actionId).getState());
            assertEquals(0, count(db, "t_incident_action_execution"));
        }
    }

    private static MockMvc mvc(ActionDbFixture db) {
        var proposals = new ActionProposalService(db.actions, db.approvals, db.guard,
                db.actionTimeline, db.alerts, db.properties);
        var decisions = new ActionDecisionService(db.actions, db.approvals, db.guard,
                db.actionTimeline, db.actionOutbox, db.properties, db.alerts);
        var transitions = new IncidentTransitionService(db.incidents, db.alerts, db.clock,
                new IncidentStateMachine(), db.incidentTimeline, db.transactions);
        transitions.setActionClosureInvalidator(new ActionClosureInvalidator(db.actions, db.approvals,
                db.executions, db.tickets, db.incidents, db.actionTimeline));
        return MockMvcBuilders.standaloneSetup(new ActionController(proposals, decisions, db.approvals),
                        new IncidentController(db.incidents, db.alerts, db.timeline, transitions,
                                new EpisodePartitioner()))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
    }

    private static String propose(MockMvc mvc, String sourceId) throws Exception {
        var response = mvc.perform(post("/incident/v1/incidents/100/actions")
                        .contentType("application/json").content(proposal(sourceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.state").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        String actionId = JSON.readTree(response).path("data").path("actionId").asText();
        assertNotNull(actionId);
        return actionId;
    }

    private static String proposal(String sourceId) {
        return "{\"sourceRequestId\":\"" + sourceId + "\",\"toolName\":\"simulation.set-state\","
                + "\"toolVersion\":\"1\",\"targetId\":\"demo\","
                + "\"parameters\":{\"desiredState\":\"PAUSED\"}}";
    }

    private static long count(ActionDbFixture db, String table) throws SQLException {
        try (var connection = db.openConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
