/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.nageoffer.ai.ragent.incident.core.service;

import com.nageoffer.ai.ragent.incident.core.dao.IncidentWorkbenchMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class IncidentWorkbenchQueryService {
    private final IncidentWorkbenchMapper mapper;
    private final TransactionTemplate transaction;
    public IncidentWorkbenchQueryService(IncidentWorkbenchMapper mapper,PlatformTransactionManager manager) {
        this.mapper=mapper;transaction=new TransactionTemplate(manager);
        transaction.setReadOnly(true);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    private static String time(OffsetDateTime value) {return value.toInstant().toString();}
    public Map<String,Object> snapshot() {
        return transaction.execute(status->{
            var at=mapper.observedAt();
            Map<String,Object> data=new LinkedHashMap<>();
            data.put("observedAt",time(at));data.put("windowStart",time(at.minusHours(24)));data.put("windowEnd",time(at));
            data.put("counts",mapper.counts());
            data.put("alertBuckets",mapper.alertBuckets(at).stream().map(b->Map.of(
                "startAt",time(b.getStartAt()),"endAt",time(b.getEndAt()),"count",b.getCount())).toList());
            data.put("attentionIncidents",mapper.attentionIncidents().stream().map(i->{
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("incidentId",i.getIncidentId());row.put("lifecycleState",i.getLifecycleState());row.put("version",i.getVersion());
                row.put("alertCount",i.getAlertCount());row.put("lastAlertReceivedAt",time(i.getLastAlertReceivedAt()));row.put("updatedAt",time(i.getUpdatedAt()));
                row.put("pendingApprovalActionCount",i.getPendingApprovalActionCount());row.put("executingActionCount",i.getExecutingActionCount());
                row.put("unknownActionCount",i.getUnknownActionCount());return row;
            }).toList());
            data.put("pendingActions",mapper.pendingActions().stream().map(a->Map.of(
                "actionId",a.getActionId(),"incidentId",a.getIncidentId(),"state",a.getState(),
                "createdAt",time(a.getCreatedAt()),"approvalDueAt",time(a.getApprovalDueAt()))).toList());
            data.put("recentActivity",mapper.recentActivity().stream().map(a->Map.of(
                "eventId",a.getEventId(),"incidentId",a.getIncidentId(),"sequenceNo",a.getSequenceNo(),
                "eventType",a.getEventType(),"createdAt",time(a.getCreatedAt()))).toList());
            return data;
        });
    }
}
