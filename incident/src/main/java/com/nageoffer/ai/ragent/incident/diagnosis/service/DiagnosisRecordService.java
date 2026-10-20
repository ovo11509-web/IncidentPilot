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
import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard;
import com.nageoffer.ai.ragent.incident.diagnosis.dao.*;
import com.nageoffer.ai.ragent.incident.diagnosis.contract.RunbookEligibility;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service public final class DiagnosisRecordService {
    private final DiagnosisRequestMapper requests; private final DiagnosisResultMapper results;
    private final ActionTransactionGuard guard; private final IncidentSnapshotService snapshots;
    private final DiagnosisTimelineWriter timeline; private final ObjectProvider<RunbookEligibility> eligibility;
    public DiagnosisRecordService(DiagnosisRequestMapper requests,DiagnosisResultMapper results,
            ActionTransactionGuard guard,IncidentSnapshotService snapshots,DiagnosisTimelineWriter timeline,
            ObjectProvider<RunbookEligibility> eligibility) {
        this.requests=requests;this.results=results;this.guard=guard;this.snapshots=snapshots;this.timeline=timeline;this.eligibility=eligibility;
    }
    public Begin begin(String incidentId,String sourceRequestId,long expectedVersion,String corpusVersion,String corpusDigest) {
        id(incidentId);safeId(sourceRequestId);String actor=actor();
        if(expectedVersion<0 || corpusVersion==null || corpusVersion.length()>64 || corpusDigest==null || !corpusDigest.matches("[0-9a-f]{64}")) throw new Failure(400,"INVALID_INPUT");
        var binding=JSON.createObjectNode().put("incidentId",incidentId).put("actor",actor).put("sourceRequestId",sourceRequestId)
            .put("expectedVersion",expectedVersion).put("version",1);
        String requestDigest=digest(binding);
        return guard.inIncident(incidentId,locked->{
            for(var old:requests.overdue(incidentId)) {
                var current=requests.lock(old.getId());
                if("RUNNING".equals(current.getState())) save(locked,current,failureResult("FAILED","DIAGNOSIS_INTERRUPTED"));
            }
            var existing=requests.bySource(incidentId,actor,sourceRequestId);
            if(existing!=null) {
                if(!requestDigest.equals(existing.getRequestDigest()))throw new Failure(409,"REQUEST_CONFLICT");
                return new Begin(existing.getId(),parse(existing.getSnapshotJson()),null,true,existing.getState());
            }
            if("CLOSED".equals(locked.row().getLifecycleState()))throw new Failure(409,"INCIDENT_CLOSED");
            if(locked.row().getVersion()!=expectedVersion)throw new Failure(409,"STALE_SNAPSHOT");
            var snapshot=snapshots.capture(locked);String token=UUID.randomUUID()+":"+UUID.randomUUID();
            var r=new DiagnosisRequestMapper.Row();r.setId(IdUtil.getSnowflakeNextIdStr());r.setIncidentId(incidentId);
            r.setActorId(actor);r.setSourceRequestId(sourceRequestId);r.setRequestDigest(requestDigest);
            r.setSnapshotJson(canonical(snapshot));r.setSnapshotDigest(snapshot.path("snapshotDigest").asText());
            r.setFactsDigest(snapshot.path("factsDigest").asText());r.setIncidentVersion(expectedVersion);
            r.setTimelineWatermark(locked.row().getTimelineSeq());r.setCorpusVersion(corpusVersion);r.setCorpusDigest(corpusDigest);
            r.setOwnerTokenDigest(hash(token));r.setState("RUNNING");r.setCreatedAt(locked.now());r.setDeadlineAt(r.getCreatedAt().plusSeconds(30));
            requests.insert(r);
            return new Begin(r.getId(),snapshot,token,false,"RUNNING");
        });
    }
    public void complete(Begin begin,JsonNode output) {
        if(begin==null || begin.ownerToken()==null)throw new Failure(403,"INVALID_COMPLETION");
        var candidate=requests.find(begin.diagnosisId());if(candidate==null)throw new Failure(404,"DIAGNOSIS_NOT_FOUND");
        guard.inIncident(candidate.getIncidentId(),locked->{
            var r=requests.lock(candidate.getId());
            if(!MessageDigest.isEqual(hash(begin.ownerToken()).getBytes(StandardCharsets.US_ASCII),r.getOwnerTokenDigest().getBytes(StandardCharsets.US_ASCII))
                    || !r.getSnapshotDigest().equals(begin.snapshot().path("snapshotDigest").asText()))throw new Failure(403,"INVALID_COMPLETION");
            if(output==null || canonical(output).getBytes(StandardCharsets.UTF_8).length>300*1024)throw new Failure(400,"INVALID_EVIDENCE");
            var prior=results.find(r.getId());
            if(prior!=null) {if(!prior.getResultDigest().equals(digest(output)))throw new Failure(409,"RESULT_CONFLICT");return null;}
            if(!locked.now().isBefore(r.getDeadlineAt())) {save(locked,r,failureResult("FAILED","DIAGNOSIS_INTERRUPTED"));return null;}
            if(!Set.of("SUPPORTED","INSUFFICIENT_EVIDENCE","INVALID_EVIDENCE","FAILED").contains(output.path("status").asText()))throw new Failure(400,"INVALID_EVIDENCE");
            if("FAILED".equals(output.path("status").asText()) && !canonical(output).equals(canonical(failureResult("FAILED",output.path("reasonCode").asText()))) )
                throw new Failure(400,"INVALID_EVIDENCE");
            if("FAILED".equals(output.path("status").asText()) && !Set.of("DIAGNOSIS_INTERRUPTED","DIAGNOSIS_UNAVAILABLE").contains(output.path("reasonCode").asText()))throw new Failure(400,"INVALID_EVIDENCE");
            var port=eligibility.getIfAvailable();
            if(!"FAILED".equals(output.path("status").asText()) && (port==null || !port.eligible(parse(r.getSnapshotJson()),output,locked.now())))
                save(locked,r,failureResult("INVALID_EVIDENCE","INVALID_EVIDENCE"));
            else save(locked,r,output);
            return null;
        });
    }
    private void save(ActionTransactionGuard.LockedIncident locked,DiagnosisRequestMapper.Row r,JsonNode output) {
        var snapshot=parse(r.getSnapshotJson());var result=new DiagnosisResultMapper.Row();result.setDiagnosisId(r.getId());
        result.setStatus(output.path("status").asText());result.setReasonCode(output.path("reasonCode").asText("NONE"));
        result.setResultJson(canonical(output));result.setResultDigest(digest(output));result.setRecordedAt(locked.now());
        result.setStaleAtPublish(locked.row().getVersion()!=r.getIncidentVersion() || !r.getFactsDigest().equals(snapshots.capture(locked).path("factsDigest").asText()));
        com.nageoffer.ai.ragent.framework.observability.ObservationScope.identify("incidentId", r.getIncidentId());
        com.nageoffer.ai.ragent.framework.observability.ObservationScope.identify("diagnosisId", r.getId());
        results.insert(result);if(requests.complete(r.getId(),r.getVersion())!=1)throw new Failure(409,"RESULT_CONFLICT");
        timeline.append(locked,r.getId(),r.getActorId(),snapshot.path("correlationId").asText(),"DIAGNOSIS_RECORDED","diagnosis:"+r.getId()+":recorded",result.getStatus());
    }
}
