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
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard.LockedIncident;
import com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.stereotype.Service;

/** Call only inside the shared Incident lock. Never exposes the original payload or labels. */
@Service
public final class IncidentSnapshotService {
    private final AlertMapper alerts;
    public IncidentSnapshotService(AlertMapper alerts){this.alerts=alerts;}
    public ObjectNode capture(LockedIncident locked) {
        var row=locked.row(); var members=alerts.findDiagnosisMembers(row.getId(),101);
        var out=DiagnosisContracts.JSON.createObjectNode().put("snapshotVersion",1)
            .put("incidentId",row.getId()).put("incidentVersion",row.getVersion())
            .put("lifecycleState",row.getLifecycleState()).put("aggregationRuleVersion",row.getAggregationRuleVersion())
            .put("capturedAt",locked.now().toString()).put("timelineWatermark",row.getTimelineSeq())
            .put("totalAlertCount",row.getAlertCount()).put("includedAlertCount",Math.min(members.size(),100))
            .put("truncated",members.size()>100 || row.getAlertCount()!=members.size());
        var list=out.putArray("alerts"); var facts=out.putArray("facts"); var scope=out.putObject("scope");
        for(var a:members.subList(0,Math.min(100,members.size()))) {
            var item=list.addObject().put("alertId",a.getId()).put("sourceId",safe(a.getSourceId()))
                .put("ruleId",safe(a.getRuleId())).put("title",safe(a.getTitle())).put("severity",a.getSeverity())
                .put("service",a.getService()).put("environment",a.getEnvironment()).put("resourceType",a.getResourceType())
                .put("resourceId",safe(a.getResourceId())).put("occurredAt",a.getOccurredAt().toString())
                .put("receivedAt",a.getReceivedAt().toString());
            if(list.size()==1) {
                scope.put("service",a.getService()).put("environment",a.getEnvironment()).put("resourceType",a.getResourceType());
                out.put("correlationId",a.getCorrelationId());
            }
            // Title is source evidence, not a measurement. Raw summary/labels can contain secrets and are omitted.
            for(String field:List.of("ruleId","title")) facts.addObject().put("factId","alert:"+a.getId()+":"+field)
                .put("sourceField",field).put("value",item.path(field).asText()).put("origin","ALERT_SOURCE_REPORT")
                .put("occurredAt",a.getOccurredAt().toString());
        }
        var content=out.deepCopy();content.remove(List.of("capturedAt","timelineWatermark","incidentVersion","incidentId","correlationId"));
        out.put("factsDigest",DiagnosisContracts.digest(content));
        out.put("snapshotDigest",DiagnosisContracts.digest(out));return out;
    }
    public static String safe(String value) {
        if(value==null)return "";
        String text=value.replaceAll("[\\p{Cntrl}]"," ")
            .replaceAll("(?i)authorization\\s*[:=]\\s*(?:Bearer\\s+)?\\S+","[REDACTED]")
            .replaceAll("(?i)(password|secret|token|api[_-]?key)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|\\S+)","[REDACTED]")
            .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*","[REDACTED]");
        return text.substring(0,Math.min(text.length(),256));
    }
}
