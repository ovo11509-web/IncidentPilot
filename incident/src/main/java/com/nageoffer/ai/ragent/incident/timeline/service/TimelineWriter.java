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

package com.nageoffer.ai.ragent.incident.timeline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Append-only safe facts; event IDs are stable business identities shared with Outbox. */
@Component
public final class TimelineWriter {
    private final TimelineMapper timeline;
    private final ObjectMapper json = new ObjectMapper();

    public TimelineWriter(TimelineMapper timeline) {
        this.timeline = timeline;
    }

    public void created(IncidentMapper.NewIncident incident, String correlationId, OffsetDateTime occurredAt) {
        var fact = base(incident.getCreatedEventId(), incident.getId(), 1,
                "incident:" + incident.getId() + ":created", "INCIDENT_CREATED", correlationId, occurredAt);
        fact.setRelatedEntityType("INCIDENT");
        fact.setRelatedEntityId(incident.getId());
        fact.setSummaryJson(encode(Map.of("aggregationKey", incident.getAggregationKey(),
                "episodeAnchorAlertId", incident.getEpisodeAnchorAlertId())));
        timeline.insert(fact);
    }

    public void attached(String incidentId, long sequenceNo, AlertMapper.AlertRow alert, String eventId,
            boolean lateAfterSeal, OffsetDateTime occurredAt) {
        var fact = base(eventId, incidentId, sequenceNo,
                "incident:" + incidentId + ":alert:" + alert.getId() + ":attached",
                lateAfterSeal ? "LATE_ALERT_ATTACHED_AFTER_SEAL" : "ALERT_ATTACHED",
                alert.getCorrelationId(), occurredAt);
        fact.setRelatedEntityType("ALERT");
        fact.setRelatedEntityId(alert.getId());
        fact.setSummaryJson(encode(Map.of("alertId", alert.getId(), "sourceId", alert.getSourceId(),
                "attachmentKind", lateAfterSeal ? "LATE_AFTER_LIFECYCLE_SEAL" : "STANDARD")));
        timeline.insert(fact);
    }

    public void statusChanged(String incidentId, long sequenceNo, String from, String to,
            String reason, String actorId, String requestId, String correlationId, OffsetDateTime occurredAt) {
        var fact = base(cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr(), incidentId, sequenceNo,
                "incident:" + incidentId + ":transition:" + requestId,
                "INCIDENT_STATUS_CHANGED", correlationId, occurredAt);
        fact.setActorType("USER");
        fact.setActorId(actorId);
        fact.setRelatedEntityType("INCIDENT");
        fact.setRelatedEntityId(incidentId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("from", from);
        summary.put("to", to);
        if (reason != null && !reason.isBlank()) summary.put("reasonDigest", digest(reason));
        fact.setSummaryJson(encode(summary));
        timeline.insert(fact);
    }

    public void aggregationSealed(String incidentId, long sequenceNo, String correlationId,
            OffsetDateTime sealedAt) {
        var fact = base(cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr(), incidentId, sequenceNo,
                "incident:" + incidentId + ":aggregation-sealed", "INCIDENT_AGGREGATION_SEALED",
                correlationId, sealedAt);
        fact.setRelatedEntityType("INCIDENT");
        fact.setRelatedEntityId(incidentId);
        fact.setSummaryJson(encode(Map.of("sealedAt", sealedAt.toString())));
        timeline.insert(fact);
    }

    private String digest(String reason) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Normalizer.normalize(reason.strip(), Normalizer.Form.NFC)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private TimelineMapper.Fact base(String id, String incidentId, long sequence, String key,
            String type, String correlationId, OffsetDateTime occurredAt) {
        var fact = new TimelineMapper.Fact();
        fact.setId(id);
        fact.setIncidentId(incidentId);
        fact.setSequenceNo(sequence);
        fact.setBusinessEventKey(key);
        fact.setEventType(type);
        fact.setActorType("SYSTEM");
        fact.setActorId("incident-worker");
        fact.setOccurredAt(occurredAt);
        fact.setCorrelationId(correlationId);
        return fact;
    }

    private String encode(Map<String, ?> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Safe fact encoding failed", e);
        }
    }
}
