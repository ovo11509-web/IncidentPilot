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

package com.nageoffer.ai.ragent.incident.diagnosis.service;
import static com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.*;
import com.nageoffer.ai.ragent.incident.diagnosis.dao.*;
import com.nageoffer.ai.ragent.incident.diagnosis.contract.RunbookEligibility;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service public final class DiagnosisQueryService {
    private final DiagnosisRequestMapper requests;private final DiagnosisResultMapper results;
    private final ActionTransactionGuard guard;private final IncidentSnapshotService snapshots;
    private final ObjectProvider<RunbookEligibility> eligibility;
    public DiagnosisQueryService(DiagnosisRequestMapper requests,DiagnosisResultMapper results,ActionTransactionGuard guard,
            IncidentSnapshotService snapshots,ObjectProvider<RunbookEligibility> eligibility) {
        this.requests=requests;this.results=results;this.guard=guard;this.snapshots=snapshots;this.eligibility=eligibility;
    }
    public Map<String,Object> get(String diagnosisId) {
        staff();id(diagnosisId);var r=requests.find(diagnosisId);if(r==null)throw new Failure(404,"DIAGNOSIS_NOT_FOUND");
        return guard.inIncident(r.getIncidentId(),locked->{
            var current=requests.find(diagnosisId);
            String facts=locked.row().getVersion()==current.getIncidentVersion()
                ?snapshots.capture(locked).path("factsDigest").asText():null;
            return view(current,results.find(diagnosisId),locked.row().getVersion(),facts,locked.now(),true);
        });
    }
    public Map<String,Object> list(String incidentId,String afterId,int limit) {
        staff();id(incidentId);if(afterId!=null)id(afterId);if(limit<1 || limit>100)throw new Failure(400,"INVALID_INPUT");
        return guard.inIncident(incidentId,locked->{
            var rows=requests.page(incidentId,afterId,limit+1);
            var page=rows.subList(0,Math.min(rows.size(),limit));
            var byId=page.isEmpty()?Map.<String,DiagnosisResultMapper.Row>of():results.findPageResults(
                page.stream().map(DiagnosisRequestMapper.Row::getId).toList()).stream().collect(
                    java.util.stream.Collectors.toMap(DiagnosisResultMapper.Row::getDiagnosisId,r->r));
            long version=locked.row().getVersion();
            String facts=page.stream().anyMatch(r->r.getIncidentVersion()==version)
                ?snapshots.capture(locked).path("factsDigest").asText():null;
            var now=locked.now();
            var items=page.stream().map(r->view(r,byId.get(r.getId()),version,facts,now,false)).toList();
            return Map.of("items",items,"hasMore",rows.size()>limit,"nextAfterId",items.isEmpty()?"":items.get(items.size()-1).get("diagnosisId"));
        });
    }
    private Map<String,Object> view(DiagnosisRequestMapper.Row current,DiagnosisResultMapper.Row result,
            long version,String facts,OffsetDateTime now,boolean detail) {
        var out=new LinkedHashMap<String,Object>();var snapshot=parse(current.getSnapshotJson());
        boolean stale=version!=current.getIncidentVersion() || !current.getFactsDigest().equals(facts);
        out.put("diagnosisId",current.getId());out.put("incidentId",current.getIncidentId());out.put("requestState",current.getState());
        if(detail)out.put("snapshot",wire(snapshot));
        out.put("snapshotDigest",current.getSnapshotDigest());out.put("deadlineAt",current.getDeadlineAt().toString());
        boolean exceeded="RUNNING".equals(current.getState()) && !now.isBefore(current.getDeadlineAt());out.put("deadlineExceeded",exceeded);
        String applicable="UNKNOWN";
        if(result!=null) {
            var payload=parse(result.getResultJson());var port=eligibility.getIfAvailable();
            applicable=port==null?"UNKNOWN":port.eligible(snapshot,payload,now)?"VALID":"INVALID";
            stale|=result.isStaleAtPublish() || !"VALID".equals(applicable);
            out.put("outcome",result.getStatus());out.put("resultDigest",result.getResultDigest());if(detail)out.put("result",wire(payload));
        }
        out.put("stale",stale);out.put("evidenceApplicability",applicable);
        out.put("nextStep",result==null?(exceeded?"REDIAGNOSE":"WAIT"):(stale?"REDIAGNOSE":"SUPPORTED".equals(result.getStatus())?"REVIEW":"NONE"));
        return out;
    }
}
