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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionQueryService;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;

class ActionQuerySnapshotIntegrationTest {
    @Test void detailDoesNotMixActionBeforeApprovalWithApprovalAfterCommit() throws Exception {
        readAcrossApproval(false);
    }

    @Test void listDoesNotMixActionBeforeApprovalWithApprovalAfterCommit() throws Exception {
        readAcrossApproval(true);
    }

    private void readAcrossApproval(boolean list) throws Exception {
        try (var db = new ActionDbFixture(); var auth = mockStatic(StpUtil.class)) {
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            var actions = mock(ActionMapper.class, AdditionalAnswers.delegatesTo(db.actions));
            var executor = Executors.newSingleThreadExecutor();
            try {
                org.mockito.stubbing.Answer<Object> afterRead = invocation -> {
                    Object old = invocation.getMethod().invoke(db.actions, invocation.getArguments());
                    executor.submit(() -> {
                        try (var writerAuth = mockStatic(StpUtil.class)) {
                            writerAuth.when(StpUtil::getLoginIdAsString).thenReturn("42");
                            new ActionDecisionService(db.actions, db.approvals, db.guard,
                                    db.actionTimeline, db.actionOutbox, db.properties, db.alerts)
                                    .decide("200", 0, "APPROVE", null, "99");
                        }
                    }).get(10, TimeUnit.SECONDS);
                    return old;
                };
                if (list) doAnswer(afterRead).when(actions).findByIncidentId("100");
                else doAnswer(afterRead).when(actions).findById("200");
                var query = new ActionQueryService(actions, db.approvals, db.executions, db.tickets,
                        db.receipts, db.incidents, db.timeline, db.transactions);
                Map<String, Object> old = list ? query.list("100").get(0) : query.get("200");
                assertEquals("PENDING_APPROVAL", old.get("state"));
                assertEquals("PENDING", ((Map<?, ?>) old.get("approval")).get("status"));
                var next = new ActionQueryService(db.actions, db.approvals, db.executions, db.tickets,
                        db.receipts, db.incidents, db.timeline, db.transactions).get("200");
                assertEquals("APPROVED", next.get("state"));
                assertEquals("APPROVED", ((Map<?, ?>) next.get("approval")).get("status"));
            } finally { executor.shutdownNow(); }
        }
    }

    @Test void listReadsTimelineOnceAndKeepsEachEntityAndSafeProjectionSeparate() throws Exception {
        try (var db = new ActionDbFixture(); var auth = mockStatic(StpUtil.class)) {
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            db.seedPendingAction("201", OffsetDateTime.now().plusMinutes(10));
            for (int i = 0; i < 3; i++) {
                var fact = new TimelineMapper.Fact();
                fact.setId("60" + i); fact.setIncidentId("100"); fact.setSequenceNo(i + 3);
                fact.setBusinessEventKey("query-fact-" + i); fact.setEventType("ACTION_STATUS_CHANGED");
                fact.setActorType("SYSTEM"); fact.setActorId("system");
                fact.setOccurredAt(OffsetDateTime.now()); fact.setCorrelationId("99");
                fact.setRelatedEntityType("ACTION"); fact.setRelatedEntityId(i == 0 ? "200" : i == 1 ? "201" : "999");
                fact.setSummaryJson("{\"token\":\"never-expose-summary\"}"); db.timeline.insert(fact);
            }
            var reads = new AtomicInteger();
            var timeline = mock(TimelineMapper.class, invocation -> {
                if (invocation.getMethod().getName().startsWith("find")) reads.incrementAndGet();
                return invocation.getMethod().invoke(db.timeline, invocation.getArguments());
            });
            var query = new ActionQueryService(db.actions, db.approvals, db.executions, db.tickets,
                    db.receipts, db.incidents, timeline, db.transactions);
            var rows = query.list("100");
            assertEquals(1, reads.get());
            assertEquals("200", rows.get(0).get("actionId"));
            assertEquals("201", rows.get(1).get("actionId"));
            for (var row : rows) {
                var facts = (List<?>) row.get("timeline"); assertEquals(1, facts.size());
                assertEquals(row.get("actionId"), ((Map<?, ?>) facts.get(0)).get("relatedEntityId"));
            }
            assertFalse(rows.toString().contains("never-expose-summary"));
            var detail = query.get("200");
            assertEquals(1, ((List<?>) detail.get("timeline")).size());
            verify(timeline).findActionFacts("100", "200", null);
            for (var fact : db.timeline.findActionFacts("100", "200", null)) {
                assertNull(fact.getId()); assertNull(fact.getActorId());
                assertEquals("{}", fact.getSummaryJson());
            }
        }
    }
}
