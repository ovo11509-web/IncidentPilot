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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionClosureInvalidator;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.TicketMapper;
import com.nageoffer.ai.ragent.incident.core.service.IncidentStateMachine;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionApprovalRaceTest {
    @Test
    void concurrentApprovalAndRejectionPersistOneDecision() throws Exception {
        try (var db = new ActionDbFixture()) {
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            var decisions = decisions(db);
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var approve = pool.submit(() -> decide(decisions, ready, go, "APPROVE"));
                var reject = pool.submit(() -> decide(decisions, ready, go, "REJECT"));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                go.countDown();
                int wins = (approve.get() ? 1 : 0) + (reject.get() ? 1 : 0);
                assertEquals(1, wins);
            } finally {
                pool.shutdownNow();
            }
            var action = db.actions.findById("200");
            var approval = db.approvals.findByActionId("200");
            assertEquals(1, action.getVersion());
            assertEquals(1, approval.getVersion());
            assertEquals("APPROVED".equals(action.getState()) ? "APPROVED" : "REJECTED", approval.getStatus());
            assertEquals(1, db.timeline.findByIncidentId("100").size());
        }
    }

    @Test
    void lockAfterDeadlineCannotGrantAndCloseFirstInvalidatesPendingAction() throws Exception {
        try (var db = new ActionDbFixture()) {
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().minusSeconds(1));
            var decisions = decisions(db);
            try (var stp = Mockito.mockStatic(StpUtil.class)) {
                stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
                assertThrows(ActionDecisionService.ConflictException.class,
                        () -> decisions.decide("200", 0, "APPROVE", null, "99"));
            }
            assertEquals("EXPIRED", db.actions.findById("200").getState());
            assertEquals("EXPIRED", db.approvals.findByActionId("200").getStatus());
        }
        try (var db = new ActionDbFixture()) {
            db.seedIncident("100");
            db.sql("UPDATE t_incident SET lifecycle_state='RESOLVED', lifecycle_sealed_at=clock_timestamp(), "
                    + "aggregation_slot_active=false WHERE id='100'");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            var transitions = new IncidentTransitionService(db.incidents, db.alerts, db.clock,
                    new IncidentStateMachine(), db.incidentTimeline, db.transactions);
            transitions.setActionClosureInvalidator(new ActionClosureInvalidator(db.actions, db.approvals,
                    db.executions, db.tickets, db.incidents, db.actionTimeline));
            transitions.transition("100", "RESOLVED", 0, "CLOSED", "resolved", "42", "close-1");
            assertEquals("EXPIRED", db.actions.findById("200").getState());
            assertEquals("EXPIRED", db.approvals.findByActionId("200").getStatus());
        }
    }

    @Test
    void approvalFirstThenCloseRevokesUnstartedDispatchAndIssuedTicket() throws Exception {
        try (var db = new ActionDbFixture(); var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.sql("UPDATE t_incident SET lifecycle_state='RESOLVED', "
                    + "lifecycle_sealed_at=clock_timestamp(), aggregation_slot_active=false WHERE id='100'");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            decisions(db).decide("200", 0, "APPROVE", null, "99");
            var action = db.actions.findById("200");
            var execution = new ExecutionMapper.NewExecution();
            execution.setId("300");
            execution.setActionId("200");
            execution.setExecutionKey(action.getExecutionKey());
            execution.setDispatchRequestId("dispatch-300");
            execution.setToolName(action.getToolName());
            execution.setToolVersion(action.getToolVersion());
            execution.setTargetType(action.getTargetType());
            execution.setTargetId(action.getTargetId());
            execution.setBindingDigest(action.getBindingDigest());
            execution.setMaxExecutionDurationMs(120000);
            execution.setReceiptGracePeriodMs(180000);
            db.executions.insert(execution);
            db.actions.transition("200", "APPROVED", 1, "DISPATCHING", null, null);
            var ticket = new TicketMapper.NewTicket();
            ticket.setId("400");
            ticket.setExecutionId("300");
            ticket.setSecretDigest("a".repeat(64));
            ticket.setIssuedAt(OffsetDateTime.now());
            ticket.setExpiresAt(OffsetDateTime.now().plusMinutes(2));
            db.tickets.insertIssued(ticket);
            var transitions = new IncidentTransitionService(db.incidents, db.alerts, db.clock,
                    new IncidentStateMachine(), db.incidentTimeline, db.transactions);
            transitions.setActionClosureInvalidator(new ActionClosureInvalidator(db.actions, db.approvals,
                    db.executions, db.tickets, db.incidents, db.actionTimeline));
            transitions.transition("100", "RESOLVED", 0, "CLOSED", "resolved", "42", "close-2");
            assertEquals("EXPIRED", db.actions.findById("200").getState());
            assertEquals("EXPIRED", db.tickets.findByExecutionId("300").get(0).getStatus());
            assertEquals("APPROVED", db.approvals.findByActionId("200").getStatus());
        }
    }

    @Test
    void databaseClockAfterLockRejectsDecisionThatStartedBeforeDeadline() throws Exception {
        try (var db = new ActionDbFixture(); var lock = db.openConnection()) {
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusSeconds(2));
            lock.setAutoCommit(false);
            try (var statement = lock.createStatement()) {
                statement.execute("SELECT pg_advisory_xact_lock(hashtextextended('alert-incident-v1:key', 0))");
            }
            var pool = Executors.newSingleThreadExecutor();
            var started = new CountDownLatch(1);
            try {
                var result = pool.submit(() -> {
                    try (var stp = Mockito.mockStatic(StpUtil.class)) {
                        stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
                        started.countDown();
                        return assertThrows(ActionDecisionService.ConflictException.class,
                                () -> decisions(db).decide("200", 0, "APPROVE", null, "99"));
                    }
                });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                Thread.sleep(2300);
                lock.commit();
                result.get(5, TimeUnit.SECONDS);
                assertEquals("EXPIRED", db.actions.findById("200").getState());
            } finally {
                pool.shutdownNow();
                lock.rollback();
            }
        }
    }

    private static ActionDecisionService decisions(ActionDbFixture db) {
        return new ActionDecisionService(db.actions, db.approvals, db.guard,
                db.actionTimeline, db.actionOutbox, db.properties, db.alerts);
    }

    private static boolean decide(ActionDecisionService service, CountDownLatch ready,
            CountDownLatch go, String decision) throws Exception {
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            ready.countDown();
            go.await();
            try {
                service.decide("200", 0, decision, "REJECT".equals(decision) ? "not safe" : null, "99");
                return true;
            } catch (ActionDecisionService.ConflictException e) {
                return false;
            }
        }
    }
}
