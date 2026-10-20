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
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Adds only safe Action facts to the existing append-only Incident timeline. */
@Component
public final class ActionTimelineWriter {
    private final TimelineMapper timeline;
    private final ObjectMapper json = new ObjectMapper();

    public ActionTimelineWriter(TimelineMapper timeline) {
        this.timeline = timeline;
    }

    public FactRef proposed(String incidentId, long sequence, String actionId, String actorType,
            String actorId, String correlationId, OffsetDateTime at) {
        return insert(incidentId, sequence, "action:" + actionId + ":proposed", "ACTION_PROPOSED",
                actorType, actorId, correlationId, "ACTION", actionId, at,
                Map.of("actionId", actionId, "state", "PENDING_APPROVAL"));
    }

    public FactRef approvalDecided(String incidentId, long sequence, String actionId, String approvalId,
            String decision, String actorId, String correlationId, OffsetDateTime at) {
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new IllegalArgumentException("invalid approval decision");
        }
        return insert(incidentId, sequence, "action:" + actionId + ":approval:" + approvalId,
                "ACTION_APPROVAL_DECIDED", "USER", actorId, correlationId, "ACTION", actionId, at,
                Map.of("actionId", actionId, "approvalId", approvalId, "decision", decision));
    }

    public FactRef statusChanged(String incidentId, long sequence, String actionId, String nextState,
            String reasonCode, String correlationId, OffsetDateTime at) {
        if (!java.util.Set.of("EXPIRED", "APPROVAL_REJECTED", "APPROVED", "UNKNOWN")
                .contains(nextState)) {
            throw new IllegalArgumentException("invalid Action state fact");
        }
        return insert(incidentId, sequence, "action:" + actionId + ":state:" + nextState,
                "ACTION_STATUS_CHANGED", "SERVICE", "incident", correlationId,
                "ACTION", actionId, at,
                Map.of("actionId", actionId, "state", nextState, "reasonCode", reasonCode));
    }

    public FactRef ticketExpired(String incidentId, long sequence, String executionId,
            String ticketId, String correlationId, OffsetDateTime at) {
        return ticketExpired(incidentId, sequence, executionId, ticketId,
                "INCIDENT_CLOSED", correlationId, at);
    }

    public FactRef ticketExpired(String incidentId, long sequence, String executionId,
            String ticketId, String reasonCode, String correlationId, OffsetDateTime at) {
        if (!java.util.Set.of("INCIDENT_CLOSED", "RECOVERY_REPLACED", "AUTHORIZATION_DEADLINE")
                .contains(reasonCode)) throw new IllegalArgumentException("invalid ticket expiry reason");
        return insert(incidentId, sequence, "ticket:" + ticketId + ":closed",
                "ACTION_TICKET_EXPIRED", "SERVICE", "incident", correlationId,
                "EXECUTION", executionId, at,
                Map.of("executionId", executionId, "ticketId", ticketId,
                        "reasonCode", reasonCode));
    }

    public FactRef dispatched(String incidentId, long sequence, String actionId,
            String executionId, String correlationId, OffsetDateTime at) {
        return insert(incidentId, sequence, "action:" + actionId + ":dispatched",
                "ACTION_DISPATCHED", "SERVICE", "incident", correlationId,
                "ACTION", actionId, at, Map.of("actionId", actionId, "executionId", executionId));
    }

    public FactRef ticketIssued(String incidentId, long sequence, String executionId,
            String ticketId, String correlationId, OffsetDateTime at) {
        return insert(incidentId, sequence, "execution:" + executionId + ":ticket:" + ticketId + ":issued",
                "ACTION_TICKET_ISSUED", "SERVICE", "incident", correlationId,
                "EXECUTION", executionId, at, Map.of("executionId", executionId, "ticketId", ticketId));
    }

    public FactRef ticketConsumed(String incidentId, long sequence, String executionId,
            String ticketId, String correlationId, OffsetDateTime at) {
        return insert(incidentId, sequence, "execution:" + executionId + ":ticket:" + ticketId + ":consumed",
                "ACTION_TICKET_CONSUMED", "SERVICE", "mcp-server", correlationId,
                "EXECUTION", executionId, at,
                Map.of("executionId", executionId, "ticketId", ticketId));
    }

    public FactRef receiptConflict(String incidentId, long sequence, String executionId,
            String receiptId, String digest, String correlationId, OffsetDateTime at) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("digest");
        return insert(incidentId, sequence,
                "execution:" + executionId + ":receipt-conflict:" + receiptId + ":" + digest,
                "ACTION_RECEIPT_CONFLICT", "SERVICE", "incident", correlationId,
                "EXECUTION", executionId, at,
                Map.of("executionId", executionId, "receiptId", receiptId, "receiptDigest", digest));
    }

    public FactRef receiptAccepted(String incidentId, long sequence, String executionId, String receiptId,
            String resultStatus, String resultCode, String correlationId, OffsetDateTime at) {
        if (!java.util.Set.of("SUCCESS", "FAILED", "REJECTED", "UNKNOWN").contains(resultStatus)) {
            throw new IllegalArgumentException("invalid receipt status");
        }
        if (resultCode == null || !resultCode.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("invalid result code");
        }
        return insert(incidentId, sequence, "execution:" + executionId + ":receipt:" + receiptId,
                "ACTION_RECEIPT_ACCEPTED", "SERVICE", "mcp-server", correlationId,
                "EXECUTION", executionId, at,
                Map.of("executionId", executionId, "receiptId", receiptId,
                        "resultStatus", resultStatus, "resultCode", resultCode));
    }

    public FactRef reconciliation(String incidentId, long sequence, String executionId,
            String evidenceStatus, String disposition, String evidenceRef,
            String correlationId, OffsetDateTime at) {
        if (!java.util.Set.of("APPLIED", "PROVEN_NOT_APPLIED").contains(evidenceStatus)
                || !java.util.Set.of("APPLIED", "CONFLICT").contains(disposition)
                || evidenceRef == null || !evidenceRef.matches("simulation-(application|rollback):[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("invalid reconciliation evidence");
        }
        return insert(incidentId, sequence,
                "execution:" + executionId + ":reconciliation:" + evidenceStatus + ":" + disposition,
                "ACTION_RECONCILIATION_RECORDED", "SERVICE", "incident",
                correlationId, "EXECUTION", executionId, at,
                Map.of("executionId", executionId, "evidenceStatus", evidenceStatus,
                        "disposition", disposition, "evidenceRef", evidenceRef));
    }

    public FactRef manualConclusion(String incidentId, long sequence, String actionId,
            String requestId, String outcome, String reason, String evidenceRef, String actorId,
            String correlationId, OffsetDateTime at) {
        if (!java.util.Set.of("SUCCEEDED", "FAILED").contains(outcome)
                || requestId == null || !requestId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("invalid manual conclusion");
        }
        return insert(incidentId, sequence, "action:" + actionId + ":manual:" + requestId,
                "ACTION_MANUAL_CONCLUSION", "USER", actorId, correlationId,
                "ACTION", actionId, at,
                Map.of("actionId", actionId, "requestId", requestId, "outcome", outcome,
                        "reason", reason, "evidenceRef", evidenceRef));
    }

    private FactRef insert(String incidentId, long sequence, String key, String type, String actorType,
            String actorId, String correlationId, String entityType, String entityId,
            OffsetDateTime at, Map<String, ?> summary) {
        var fact = new TimelineMapper.Fact();
        fact.setId(cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr());
        fact.setIncidentId(incidentId);
        fact.setSequenceNo(sequence);
        fact.setBusinessEventKey(key);
        fact.setEventType(type);
        fact.setActorType(actorType);
        fact.setActorId(actorId);
        fact.setOccurredAt(at);
        fact.setCorrelationId(correlationId);
        fact.setRelatedEntityType(entityType);
        fact.setRelatedEntityId(entityId);
        try {
            fact.setSummaryJson(json.writeValueAsString(summary));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Action fact encoding failed", e);
        }
        timeline.insert(fact);
        return new FactRef(fact.getId(), key);
    }

    public record FactRef(String id, String businessKey) {}
}
