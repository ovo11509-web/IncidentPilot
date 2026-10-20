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
import static com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.*;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.diagnosis.contract.*;
import com.nageoffer.ai.ragent.incident.diagnosis.config.*;
import com.nageoffer.ai.ragent.incident.diagnosis.service.*;
import com.nageoffer.ai.ragent.incident.action.service.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.junit.jupiter.api.Test;
import java.util.*;
class DiagnosisActionSelectionIntegrationTest {
    @Test void requestResultSelectionIdempotencyAndRollback() throws Exception {
        try(var db=new DiagnosisDbFixture();var auth=mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("42");db.seedIncident("100");db.associate();
            var factory=new StaticListableBeanFactory();factory.addBean("eligibility",(RunbookEligibility)(s,r,t)->true);
            var provider=factory.getBeanProvider(RunbookEligibility.class);var snapshots=new IncidentSnapshotService(db.alerts);var writer=new DiagnosisTimelineWriter(db.timeline);
            var records=new DiagnosisRecordService(db.requests,db.results,db.guard,snapshots,writer,provider);
            var b=records.begin("100","diagnose-1",0,"v1","a".repeat(64));
            assertTrue(records.begin("100","diagnose-1",0,"v1","a".repeat(64)).repeated());
            assertThrows(Failure.class,()->records.begin("100","diagnose-1",1,"v1","a".repeat(64)));
            var result=failureResult("SUPPORTED","SOURCE_REPORT_ONLY");var rec=result.withArray("recommendations").addObject().put("recommendationId","R2")
                .put("convertible",true).put("ruleId","RB-HEALTH:simulation-recovery-v1").put("desiredState","READY").put("recommendationDigest","b".repeat(64));
            records.complete(b,result);records.complete(b,result);assertEquals(1,db.timeline.findByIncidentId("100").size());
            db.sql("UPDATE t_incident_diagnosis_request SET created_at=clock_timestamp()-interval '40 seconds',deadline_at=clock_timestamp()-interval '1 second' WHERE id='"+b.diagnosisId()+"'");
            assertEquals(false,new DiagnosisQueryService(db.requests,db.results,db.guard,snapshots,provider).get(b.diagnosisId()).get("deadlineExceeded"));
            assertThrows(Failure.class,()->records.complete(new Begin(b.diagnosisId(),b.snapshot(),"forged",false,"RUNNING"),result));
            var properties=new DiagnosisProperties();properties.setSimulationTargets(Map.of("incident-demo:demo:sim_service","demo"));
            var proposals=new ActionProposalService(db.actions,db.approvals,db.guard,db.actionTimeline,db.alerts,db.properties);
            var selection=new DiagnosisActionSelectionService(db.requests,db.results,db.links,db.guard,snapshots,writer,proposals,properties,db.properties,provider);
            var input=JSON.createObjectNode().put("sourceRequestId","select-1").put("expectedIncidentVersion",0).put("diagnosisDigest",digest(result)).put("recommendationDigest","b".repeat(64));
            var a=selection.select(b.diagnosisId(),"R2",input);assertFalse(a.repeated());assertEquals("PENDING_APPROVAL",db.actions.findById(a.actionId()).getState());
            assertNotNull(db.approvals.findByActionId(a.actionId()));assertNotNull(db.links.find(a.actionId()));assertEquals(3,db.timeline.findByIncidentId("100").size());
            assertTrue(selection.select(b.diagnosisId(),"R2",input).repeated());
            var conflict=input.deepCopy().put("expectedIncidentVersion",1);assertThrows(Failure.class,()->selection.select(b.diagnosisId(),"R2",conflict));
            db.sql("UPDATE t_incident SET version=version+1 WHERE id='100'");
            assertTrue(selection.select(b.diagnosisId(),"R2",input).repeated());
            var stale=input.deepCopy().put("sourceRequestId","select-2");assertThrows(Failure.class,()->selection.select(b.diagnosisId(),"R2",stale));
            assertEquals(1,db.actions.findByIncidentId("100").size());
            // Failure after creation must roll back Action, Approval, link and sequence together.
            db.sql("UPDATE t_incident SET version=0 WHERE id='100'");
            var faultyLinks=spy(db.links);doThrow(new IllegalStateException("injected rollback")).when(faultyLinks).insert(any());
            var faulty=new DiagnosisActionSelectionService(db.requests,db.results,faultyLinks,db.guard,snapshots,writer,proposals,properties,db.properties,provider);
            assertThrows(IllegalStateException.class,()->faulty.select(b.diagnosisId(),"R2",stale));
            assertEquals(1,db.actions.findByIncidentId("100").size());assertEquals(3,db.timeline.findByIncidentId("100").size());
            // Separate sessions race one source request; the DB Incident lock serializes both.
            var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var concurrent=input.deepCopy().put("sourceRequestId","concurrent");
                java.util.concurrent.Callable<String> choose=()->{try(var workerAuth=mockStatic(StpUtil.class)){
                    workerAuth.when(StpUtil::getLoginIdAsString).thenReturn("42");return selection.select(b.diagnosisId(),"R2",concurrent).actionId();}};
                var one=executor.submit(choose);var two=executor.submit(choose);assertEquals(one.get(),two.get());
                assertEquals(2,db.actions.findByIncidentId("100").size());assertEquals(5,db.timeline.findByIncidentId("100").size());
            }finally{executor.shutdownNow();}
        }
    }
    @Test void overdueIsReadonlyUntilNextPostAndTerminalIsUnique() throws Exception {
        try(var db=new DiagnosisDbFixture();var auth=mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("42");db.seedIncident("100");db.associate();
            var provider=new StaticListableBeanFactory().getBeanProvider(RunbookEligibility.class);var snapshots=new IncidentSnapshotService(db.alerts);
            var service=new DiagnosisRecordService(db.requests,db.results,db.guard,snapshots,new DiagnosisTimelineWriter(db.timeline),provider);
            var b=service.begin("100","interrupted",0,"v1","a".repeat(64));
            db.sql("UPDATE t_incident_diagnosis_request SET created_at=clock_timestamp()-interval '40 seconds',deadline_at=clock_timestamp()-interval '1 second' WHERE id='"+b.diagnosisId()+"'");
            var queries=new DiagnosisQueryService(db.requests,db.results,db.guard,snapshots,provider);
            assertEquals(true,queries.get(b.diagnosisId()).get("deadlineExceeded"));assertNull(db.results.find(b.diagnosisId()));
            assertEquals(1,((java.util.List<?>)queries.list("100",null,20).get("items")).size());
            service.begin("100","next",0,"v1","a".repeat(64));assertEquals("FAILED",db.results.find(b.diagnosisId()).getStatus());
            assertEquals(1,db.timeline.findByIncidentId("100").size());
        }
    }
}
