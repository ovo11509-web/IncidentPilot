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

package com.nageoffer.ai.ragent.incident.contract;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.outbox.dao.OutboxMapper;
import com.nageoffer.ai.ragent.incident.outbox.service.IncidentEventWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IncidentEventContractTest {
    @Test
    void bothV1EventsHaveExactEnvelopeFieldsStableIdentityAndSafePayload() throws Exception {
        var mapper = mock(OutboxMapper.class);
        var writer = new IncidentEventWriter(mapper);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var incident = new IncidentMapper.NewIncident();
        incident.setId("100");
        incident.setCreatedEventId("101");
        incident.setAggregationKey("alert-incident-v1:" + "a".repeat(64));
        incident.setAggregationRuleVersion("alert-incident-v1");
        incident.setAggregationWindowSeconds(1800);
        incident.setEpisodeStartAt(now);
        incident.setEpisodeAnchorAlertId("200");
        incident.setFirstAlertReceivedAt(now);
        incident.setLastAlertReceivedAt(now);
        incident.setAlertCount(1);
        var alert = new AlertMapper.AlertRow();
        alert.setId("200");
        alert.setSourceId("monitor");
        alert.setReceivedAt(now);
        alert.setFingerprint("a".repeat(64));
        alert.setCorrelationId("300");
        writer.created(incident, "300", 1, now);
        writer.attached("100", alert, "102", 1, false, now);
        var capture = forClass(OutboxMapper.NewEvent.class);
        verify(mapper, times(2)).insert(capture.capture());
        assertEquals(Set.of("incident.created", "incident.alert-attached"),
                capture.getAllValues().stream().map(OutboxMapper.NewEvent::getEventType).collect(java.util.stream.Collectors.toSet()));
        var json = new ObjectMapper();
        for (var event : capture.getAllValues()) {
            var root = json.readTree(event.getPayloadJson());
            assertEquals(Set.of("eventId", "schemaVersion", "eventType", "incidentId", "aggregateVersion",
                    "correlationId", "occurredAt", "payload"),
                    fieldNames(root));
            assertEquals(1, root.get("schemaVersion").intValue());
            assertEquals(1, root.get("aggregateVersion").longValue());
            assertEquals(event.getEventId(), root.get("eventId").asText());
            assertEquals(event.getEventType(), root.get("eventType").asText());
            assertFalse(event.getPayloadJson().contains("SECRET_PROBE"));
            assertFalse(event.getPayloadJson().contains("title"));
            assertFalse(event.getPayloadJson().contains("labels"));
        }
        assertEquals(Set.of("aggregationKey", "aggregationRuleVersion", "aggregationWindowSeconds",
                "episodeStartAt", "episodeAnchorAlertId", "firstAlertReceivedAt", "lastAlertReceivedAt",
                "alertCount", "lifecycleState", "bornSealed"),
                fieldNames(json.readTree(capture.getAllValues().get(0).getPayloadJson()).get("payload")));
        assertEquals(Set.of("alertId", "sourceId", "receivedAt", "attachmentKind", "fingerprint"),
                fieldNames(json.readTree(capture.getAllValues().get(1).getPayloadJson()).get("payload")));
    }

    private static Set<String> fieldNames(com.fasterxml.jackson.databind.JsonNode node) {
        var fields = new java.util.HashSet<String>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
