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
import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.incident.action.service.ActionTransactionGuard.LockedIncident;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts;
import org.springframework.stereotype.Service;

@Service public final class DiagnosisTimelineWriter {
    private final TimelineMapper timeline;
    public DiagnosisTimelineWriter(TimelineMapper timeline){this.timeline=timeline;}
    public void append(LockedIncident locked,String diagnosisId,String actor,String correlation,String type,String key,String status) {
        var f=new TimelineMapper.Fact();f.setId(IdUtil.getSnowflakeNextIdStr());f.setIncidentId(locked.row().getId());
        f.setSequenceNo(locked.reserveTimeline(1));f.setBusinessEventKey(key);f.setEventType(type);f.setActorType("USER");
        f.setActorId(actor);f.setOccurredAt(locked.now());f.setCorrelationId(correlation);
        f.setRelatedEntityType("DIAGNOSIS");f.setRelatedEntityId(diagnosisId);
        f.setSummaryJson(DiagnosisContracts.canonical(DiagnosisContracts.JSON.createObjectNode().put("diagnosisId",diagnosisId).put("status",status)));
        timeline.insert(f);
    }
}
