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

package com.nageoffer.ai.ragent.incident.diagnosis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.diagnosis.contract.RunbookEligibility;
import com.nageoffer.ai.ragent.incident.diagnosis.dao.DiagnosisResultMapper;
import com.nageoffer.ai.ragent.incident.diagnosis.service.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class DiagnosisQueryPageIntegrationTest {
    @Test void runningPageKeepsDeadlineAndDetailSemanticsWithoutResults() throws Exception {
        try (var db = new DiagnosisDbFixture(); var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100"); db.associate();
            var provider = new StaticListableBeanFactory().getBeanProvider(RunbookEligibility.class);
            var snapshots = new IncidentSnapshotService(db.alerts);
            var records = new DiagnosisRecordService(db.requests, db.results, db.guard, snapshots,
                    new DiagnosisTimelineWriter(db.timeline), provider);
            var expired = records.begin("100", "expired", 0, "v1", "a".repeat(64));
            var active = records.begin("100", "active", 0, "v1", "a".repeat(64));
            db.sql("UPDATE t_incident_diagnosis_request SET created_at=clock_timestamp()-interval '2 minutes',"
                    + "deadline_at=clock_timestamp()-interval '1 minute' WHERE id='" + expired.diagnosisId() + "'");
            var query = new DiagnosisQueryService(db.requests, db.results, db.guard, snapshots, provider);
            var items = (List<?>) query.list("100", null, 100).get("items");
            assertEquals(2, items.size());
            for (var item : items) {
                var row = (Map<?, ?>) item;
                boolean isExpired = expired.diagnosisId().equals(row.get("diagnosisId"));
                assertEquals(isExpired, row.get("deadlineExceeded"));
                assertEquals(isExpired ? "REDIAGNOSE" : "WAIT", row.get("nextStep"));
                assertEquals("UNKNOWN", row.get("evidenceApplicability")); assertEquals(false, row.get("stale"));
                var detail = query.get((String) row.get("diagnosisId"));
                assertTrue(detail.containsKey("snapshot")); detail.remove("snapshot"); assertEquals(row, detail);
            }
            assertNotEquals(expired.diagnosisId(), active.diagnosisId());
        }
    }

    @Test void pageSharesFactsAndResultReadButKeepsPerDiagnosisValidity() throws Exception {
        try (var db = new DiagnosisDbFixture(); var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100"); db.associate();
            var observations = new java.util.ArrayList<java.time.OffsetDateTime>();
            var factory = new StaticListableBeanFactory();
            var applicable = new java.util.concurrent.atomic.AtomicBoolean(true);
            factory.addBean("eligibility", (RunbookEligibility) (s, r, at) -> {
                observations.add(at); return applicable.get();
            });
            var provider = factory.getBeanProvider(RunbookEligibility.class);
            var snapshots = new IncidentSnapshotService(db.alerts);
            var records = new DiagnosisRecordService(db.requests, db.results, db.guard, snapshots,
                    new DiagnosisTimelineWriter(db.timeline), provider);
            for (int i = 0; i < 3; i++) {
                var begin = records.begin("100", "page-" + i, 0, "v1", "a".repeat(64));
                records.complete(begin, com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.failureResult("SUPPORTED", "SOURCE_REPORT_ONLY"));
            }
            observations.clear();
            var captures = spy(new IncidentSnapshotService(db.alerts));
            var reads = new AtomicInteger();
            var resultIds = new java.util.ArrayList<String>();
            var results = mock(DiagnosisResultMapper.class, invocation -> {
                if (invocation.getMethod().getName().startsWith("find")) reads.incrementAndGet();
                if (invocation.getMethod().getName().equals("findPageResults")) {
                    resultIds.clear(); resultIds.addAll(invocation.getArgument(0));
                }
                return invocation.getMethod().invoke(db.results, invocation.getArguments());
            });
            var query = new DiagnosisQueryService(db.requests, results, db.guard, captures, provider);
            var page = query.list("100", null, 2);
            var items = (List<?>) page.get("items");
            assertEquals(2, items.size()); assertEquals(true, page.get("hasMore"));
            verify(captures, times(1)).capture(any());
            assertEquals(1, reads.get());
            assertEquals(items.stream().map(item -> ((Map<?, ?>) item).get("diagnosisId")).toList(), resultIds);
            assertEquals(2, observations.size()); assertEquals(observations.get(0), observations.get(1));
            for (var item : items) {
                var row = (Map<?, ?>) item;
                assertFalse(row.containsKey("snapshot")); assertFalse(row.containsKey("result"));
                assertEquals(false, row.get("stale")); assertEquals("VALID", row.get("evidenceApplicability"));
            }
            applicable.set(false);
            for (var item : (List<?>) query.list("100", null, 2).get("items")) {
                var row = (Map<?, ?>) item;
                assertEquals("INVALID", row.get("evidenceApplicability"));
                assertEquals(true, row.get("stale")); assertEquals("REDIAGNOSE", row.get("nextStep"));
            }
            applicable.set(true);
            clearInvocations(captures); reads.set(0);
            db.sql("UPDATE t_incident SET version=1 WHERE id='100'");
            var stale = (List<?>) query.list("100", null, 2).get("items");
            verify(captures, never()).capture(any());
            assertEquals(true, ((Map<?, ?>) stale.get(0)).get("stale"));
            assertEquals(1, reads.get());
            var last = (Map<?, ?>) ((List<?>) query.list("100", null, 100).get("items")).get(2);
            assertEquals(3, resultIds.size());
            clearInvocations(captures); reads.set(0);
            var empty = query.list("100", (String) last.get("diagnosisId"), 100);
            assertTrue(((List<?>) empty.get("items")).isEmpty());
            assertEquals(false, empty.get("hasMore"));
            verify(captures, never()).capture(any()); assertEquals(0, reads.get());
        }
    }
}
