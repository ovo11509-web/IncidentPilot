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

package com.nageoffer.ai.ragent.incident.action.timeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.outbox.dao.OutboxMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Emits only the two Spec B event types through the existing Outbox relay. */
@Component
public final class ActionOutboxWriter {
    private final OutboxMapper outbox;
    private final ObjectMapper json = new ObjectMapper();

    public ActionOutboxWriter(OutboxMapper outbox) {
        this.outbox = outbox;
    }

    public void approvalDecided(ActionTimelineWriter.FactRef fact, ActionMapper.ActionRow action,
            ApprovalMapper.ApprovalRow approval, long incidentVersion, String correlationId, OffsetDateTime at) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("actionId", action.getId());
        payload.put("actionVersion", action.getVersion());
        payload.put("approvalId", approval.getId());
        payload.put("decision", approval.getStatus());
        payload.put("bindingVersion", action.getBindingVersion());
        payload.put("bindingDigest", action.getBindingDigest());
        if ("APPROVED".equals(approval.getStatus())) {
            payload.put("authorizationDueAt", approval.getAuthorizationDueAt().toString());
        } else if ("REJECTED".equals(approval.getStatus())) {
            if (approval.getReasonCode() == null || !approval.getReasonCode().matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw new IllegalArgumentException("invalid rejection code");
            }
            payload.put("reasonCode", approval.getReasonCode());
        } else {
            throw new IllegalArgumentException("not a personnel decision");
        }
        payload.put("actorId", approval.getDeciderId());
        insert(fact, action.getIncidentId(), incidentVersion, "incident.action-approval-decided",
                correlationId, at, payload);
    }

    public void receiptAccepted(ActionTimelineWriter.FactRef fact, ActionMapper.ActionRow action,
            ReceiptMapper.ReceiptRow receipt, long incidentVersion, String correlationId, OffsetDateTime at) {
        if (!"APPLIED".equals(receipt.getDisposition())) {
            throw new IllegalArgumentException("receipt was not applied");
        }
        if (receipt.getResultCode() == null || !receipt.getResultCode().matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("invalid result code");
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("actionId", action.getId());
        payload.put("actionVersion", action.getVersion());
        payload.put("executionId", receipt.getExecutionId());
        payload.put("receiptId", receipt.getId());
        payload.put("resultStatus", receipt.getResultStatus());
        payload.put("resultCode", receipt.getResultCode());
        payload.put("toolName", receipt.getToolName());
        payload.put("toolVersion", receipt.getToolVersion());
        payload.put("targetType", receipt.getTargetType());
        payload.put("targetId", receipt.getTargetId());
        insert(fact, action.getIncidentId(), incidentVersion, "incident.action-receipt-accepted",
                correlationId, at, payload);
    }

    private void insert(ActionTimelineWriter.FactRef fact, String incidentId, long incidentVersion,
            String eventType, String correlationId, OffsetDateTime at, Map<String, ?> payload) {
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", fact.id());
        envelope.put("schemaVersion", 1);
        envelope.put("eventType", eventType);
        envelope.put("incidentId", incidentId);
        envelope.put("aggregateVersion", incidentVersion);
        envelope.put("correlationId", correlationId);
        envelope.put("occurredAt", at.toString());
        envelope.put("payload", payload);
        var event = new OutboxMapper.NewEvent();
        event.setEventId(fact.id());
        event.setBusinessEventKey(fact.businessKey());
        event.setIncidentId(incidentId);
        event.setAggregateVersion(incidentVersion);
        event.setEventType(eventType);
        event.setCorrelationId(correlationId);
        event.setOccurredAt(at);
        try {
            event.setPayloadJson(json.writeValueAsString(envelope));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Action event encoding failed", e);
        }
        outbox.insert(event);
    }
}
