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
import com.nageoffer.ai.ragent.incident.diagnosis.config.DiagnosisProperties;
import com.nageoffer.ai.ragent.incident.action.config.ActionProperties;
import com.nageoffer.ai.ragent.incident.action.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service public final class DiagnosisActionSelectionService {
    private final DiagnosisRequestMapper requests;private final DiagnosisResultMapper results;private final DiagnosisActionLinkMapper links;
    private final ActionTransactionGuard guard;private final IncidentSnapshotService snapshots;private final DiagnosisTimelineWriter timeline;
    private final ActionProposalService proposals;private final DiagnosisProperties properties;private final ActionProperties actions;
    private final ObjectProvider<RunbookEligibility> eligibility;
    public DiagnosisActionSelectionService(DiagnosisRequestMapper requests,DiagnosisResultMapper results,DiagnosisActionLinkMapper links,
            ActionTransactionGuard guard,IncidentSnapshotService snapshots,DiagnosisTimelineWriter timeline,ActionProposalService proposals,
            DiagnosisProperties properties,ActionProperties actions,ObjectProvider<RunbookEligibility> eligibility) {
        this.requests=requests;this.results=results;this.links=links;this.guard=guard;this.snapshots=snapshots;this.timeline=timeline;
        this.proposals=proposals;this.properties=properties;this.actions=actions;this.eligibility=eligibility;
    }
    public Selection select(String diagnosisId,String recommendationId,JsonNode body) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.action.propose", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        id(diagnosisId);safeId(recommendationId);String actor=actor();String source=safeId(text(body,"sourceRequestId"));
        long expected=version(body,"expectedIncidentVersion");String diagnosisDigest=text(body,"diagnosisDigest"),recDigest=text(body,"recommendationDigest");
        String original=body.has("originalActionId")?text(body,"originalActionId"):null;if(original!=null)id(original);
        var r=requests.find(diagnosisId);if(r==null)throw new Failure(404,"DIAGNOSIS_NOT_FOUND");
        var binding=body.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)binding).put("diagnosisId",diagnosisId).put("recommendationId",recommendationId).put("actorId",actor);
        String selectionDigest=digest(binding);
        var selected = guard.inIncident(r.getIncidentId(),locked->{
            var prior=links.bySource(r.getIncidentId(),actor,source);
            if(prior!=null) {
                if(!prior.getSelectionDigest().equals(selectionDigest))throw new Failure(409,"REQUEST_CONFLICT");
                var action=proposals.find(prior.getActionId());return new Selection(action.getId(),true,action.getState());
            }
            var result=results.find(diagnosisId);
            if(result==null || !"SUPPORTED".equals(result.getStatus()))throw new Failure(400,"UNSUPPORTED_RECOMMENDATION");
            if(!result.getResultDigest().equals(diagnosisDigest))throw new Failure(409,"REQUEST_CONFLICT");
            var output=parse(result.getResultJson());var snapshot=parse(r.getSnapshotJson());
            JsonNode rec=null;for(var item:output.path("recommendations"))if(recommendationId.equals(item.path("recommendationId").asText()))rec=item;
            if(rec==null)throw new Failure(404,"RECOMMENDATION_NOT_FOUND");
            if(!rec.path("recommendationDigest").asText().equals(recDigest))throw new Failure(409,"REQUEST_CONFLICT");
            if(!rec.path("convertible").asBoolean() || !"RB-HEALTH:simulation-recovery-v1".equals(rec.path("ruleId").asText())
                || !"READY".equals(rec.path("desiredState").asText()))throw new Failure(400,"UNSUPPORTED_RECOMMENDATION");
            if("CLOSED".equals(locked.row().getLifecycleState()))throw new Failure(409,"INCIDENT_CLOSED");
            if(expected!=r.getIncidentVersion() || expected!=locked.row().getVersion() || result.isStaleAtPublish()
                || snapshot.path("truncated").asBoolean() || !r.getFactsDigest().equals(snapshots.capture(locked).path("factsDigest").asText()))throw new Failure(409,"STALE_SNAPSHOT");
            var port=eligibility.getIfAvailable();if(port==null || !port.eligible(snapshot,output,locked.now()))throw new Failure(409,"EVIDENCE_REVOKED");
            var scope=snapshot.path("scope");String key=scope.path("service").asText()+":"+scope.path("environment").asText()+":"+scope.path("resourceType").asText();
            String target=properties.getSimulationTargets().get(key);
            if(!"incident-demo:demo:sim_service".equals(key) || target==null || !actions.getAllowedSimulationTargets().contains(target))throw new Failure(400,"UNSUPPORTED_RECOMMENDATION");
            // Namespace the existing B source key; digest includes actor and complete selection binding.
            var proposed=proposals.proposeUser(new ActionProposalService.UserProposal(r.getIncidentId(),"diagnosis:"+selectionDigest,original,
                "simulation.set-state","1",target,"{\"desiredState\":\"READY\"}"));
            if(proposed.repeated())throw new Failure(409,"REQUEST_CONFLICT");
            var link=new DiagnosisActionLinkMapper.Row();link.setActionId(proposed.action().getId());link.setIncidentId(r.getIncidentId());link.setDiagnosisId(diagnosisId);
            link.setRecommendationId(recommendationId);link.setRecommendationDigest(recDigest);link.setDiagnosisDigest(diagnosisDigest);link.setFactsDigest(r.getFactsDigest());
            link.setActorId(actor);link.setSourceRequestId(source);link.setSelectionDigest(selectionDigest);link.setCreatedAt(locked.now());links.insert(link);
            // Reacquire the current timeline row inside the same transaction after B reserved its sequence.
            guard.inIncident(r.getIncidentId(),fresh->{timeline.append(fresh,diagnosisId,actor,snapshot.path("correlationId").asText(),
                "DIAGNOSIS_ACTION_LINKED","diagnosis:action:"+link.getActionId(),"PENDING_APPROVAL");return null;});
            return new Selection(link.getActionId(),false,"PENDING_APPROVAL");
        });
        observation.id("diagnosisId", r.getId()).id("incidentId", r.getIncidentId()).id("actionId", selected.actionId())
                .id("sourceRequestId", source).outcome(selected.repeated() ? "REUSED" : "PENDING_APPROVAL");
        return selected;
        }
    }
    public record Selection(String actionId,boolean repeated,String state){}
}
