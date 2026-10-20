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
import java.util.Map;
class DiagnosisActionSecurityTest {
    @Test void missingAdapterRejectsEvidenceAndSelectionNeverCreatesActionOrTicket() throws Exception {
        try(var db=new DiagnosisDbFixture();var auth=mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("42");db.seedIncident("100");db.associate();
            var provider=new StaticListableBeanFactory().getBeanProvider(RunbookEligibility.class);var snapshots=new IncidentSnapshotService(db.alerts);var writer=new DiagnosisTimelineWriter(db.timeline);
            var records=new DiagnosisRecordService(db.requests,db.results,db.guard,snapshots,writer,provider);
            var b=records.begin("100","unsupported",0,"v1","a".repeat(64));var forged=failureResult("SUPPORTED","NONE");
            forged.withArray("recommendations").addObject().put("convertible",true).put("desiredState","DESTROY");records.complete(b,forged);
            assertEquals("INVALID_EVIDENCE",db.results.find(b.diagnosisId()).getStatus());
            var properties=new DiagnosisProperties();properties.setSimulationTargets(Map.of("incident-demo:demo:sim_service","production"));
            var proposals=new ActionProposalService(db.actions,db.approvals,db.guard,db.actionTimeline,db.alerts,db.properties);
            var selection=new DiagnosisActionSelectionService(db.requests,db.results,db.links,db.guard,snapshots,writer,proposals,properties,db.properties,provider);
            var input=JSON.createObjectNode().put("sourceRequestId","select").put("expectedIncidentVersion",0).put("diagnosisDigest",digest(forged)).put("recommendationDigest","a".repeat(64));
            assertThrows(Failure.class,()->selection.select(b.diagnosisId(),"R2",input));assertTrue(db.actions.findByIncidentId("100").isEmpty());
            try(var c=db.openConnection();var s=c.createStatement();var rs=s.executeQuery("SELECT count(*) FROM t_incident_execution_ticket")){rs.next();assertEquals(0,rs.getInt(1));}
            assertEquals("UNKNOWN",new DiagnosisQueryService(db.requests,db.results,db.guard,snapshots,provider).get(b.diagnosisId()).get("evidenceApplicability"));
        }
    }
    @Test void scopeMappingCannotIntroduceRealResourceTypes(){var p=new DiagnosisProperties();p.setSimulationTargets(Map.of("prod:prod:host","production"));assertThrows(IllegalArgumentException.class,p::validate);}
}
