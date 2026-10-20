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

package com.nageoffer.ai.ragent.incident.outbox.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.outbox.dao.OutboxMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Writes only the two Spec A domain event types into the current transaction. */
@Component
public final class IncidentEventWriter {
    private final OutboxMapper outbox;
    private final ObjectMapper json = new ObjectMapper();

    public IncidentEventWriter(OutboxMapper outbox) {
        this.outbox = outbox;
    }

    public void created(IncidentMapper.NewIncident incident, String correlationId,
            long finalVersion, OffsetDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("aggregationKey", incident.getAggregationKey());
        payload.put("aggregationRuleVersion", incident.getAggregationRuleVersion());
        payload.put("aggregationWindowSeconds", incident.getAggregationWindowSeconds());
        payload.put("episodeStartAt", incident.getEpisodeStartAt().toString());
        payload.put("episodeAnchorAlertId", incident.getEpisodeAnchorAlertId());
        payload.put("firstAlertReceivedAt", incident.getFirstAlertReceivedAt().toString());
        payload.put("lastAlertReceivedAt", incident.getLastAlertReceivedAt().toString());
        payload.put("alertCount", incident.getAlertCount());
        payload.put("lifecycleState", "OPEN");
        payload.put("bornSealed", incident.isBornSealed());
        insert(incident.getCreatedEventId(), "incident:" + incident.getId() + ":created", incident.getId(),
                finalVersion, "incident.created", correlationId, occurredAt, payload);
    }

    public void attached(String incidentId, AlertMapper.AlertRow alert, String eventId,
            long finalVersion, boolean lateAfterSeal, OffsetDateTime occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alertId", alert.getId());
        payload.put("sourceId", alert.getSourceId());
        payload.put("receivedAt", alert.getReceivedAt().toString());
        payload.put("attachmentKind", lateAfterSeal ? "LATE_AFTER_LIFECYCLE_SEAL" : "STANDARD");
        payload.put("fingerprint", alert.getFingerprint());
        insert(eventId, "incident:" + incidentId + ":alert:" + alert.getId() + ":attached",
                incidentId, finalVersion, "incident.alert-attached", alert.getCorrelationId(), occurredAt, payload);
    }

    private void insert(String eventId, String businessKey, String incidentId, long finalVersion,
            String type, String correlationId, OffsetDateTime occurredAt, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("schemaVersion", 1);
        envelope.put("eventType", type);
        envelope.put("incidentId", incidentId);
        envelope.put("aggregateVersion", finalVersion);
        envelope.put("correlationId", correlationId);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("payload", payload);
        var event = new OutboxMapper.NewEvent();
        event.setEventId(eventId);
        event.setBusinessEventKey(businessKey);
        event.setIncidentId(incidentId);
        event.setAggregateVersion(finalVersion);
        event.setEventType(type);
        event.setCorrelationId(correlationId);
        event.setOccurredAt(occurredAt);
        try {
            event.setPayloadJson(json.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Domain event encoding failed", e);
        }
        outbox.insert(event);
    }
}
