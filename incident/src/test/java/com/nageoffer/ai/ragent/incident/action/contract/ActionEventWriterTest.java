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

package com.nageoffer.ai.ragent.incident.action.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ActionMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ApprovalMapper;
import com.nageoffer.ai.ragent.incident.action.dao.ReceiptMapper;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionOutboxWriter;
import com.nageoffer.ai.ragent.incident.action.timeline.ActionTimelineWriter;
import com.nageoffer.ai.ragent.incident.outbox.dao.OutboxMapper;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ActionEventWriterTest {
    @Test
    void approvalAndReceiptReuseTimelineIdentityAndOnlyPublishSafePayload() throws Exception {
        var timelineMapper = mock(TimelineMapper.class);
        var outboxMapper = mock(OutboxMapper.class);
        var timeline = new ActionTimelineWriter(timelineMapper);
        var outbox = new ActionOutboxWriter(outboxMapper);
        var at = OffsetDateTime.now();
        var action = new ActionMapper.ActionRow();
        action.setId("action-1");
        action.setIncidentId("incident-1");
        action.setVersion(1);
        action.setBindingVersion("action-binding-v1");
        action.setBindingDigest("a".repeat(64));
        action.setParametersJson("secret-parameters-must-not-leak");
        var approval = new ApprovalMapper.ApprovalRow();
        approval.setId("approval-1");
        approval.setStatus("APPROVED");
        approval.setAuthorizationDueAt(at.plusMinutes(5));
        approval.setDeciderId("user-1");
        var approvalFact = timeline.approvalDecided("incident-1", 3, "action-1", "approval-1",
                "APPROVED", "user-1", "correlation-1", at);
        outbox.approvalDecided(approvalFact, action, approval, 2, "correlation-1", at);

        var receipt = new ReceiptMapper.ReceiptRow();
        receipt.setId("receipt-1");
        receipt.setExecutionId("execution-1");
        receipt.setDisposition("APPLIED");
        receipt.setResultStatus("SUCCESS");
        receipt.setResultCode("SIMULATION_APPLIED");
        receipt.setToolName("simulation.set-state");
        receipt.setToolVersion("1");
        receipt.setTargetType("SIM_SERVICE");
        receipt.setTargetId("demo");
        var receiptFact = timeline.receiptAccepted("incident-1", 4, "execution-1", "receipt-1",
                "SUCCESS", "SIMULATION_APPLIED", "correlation-1", at);
        outbox.receiptAccepted(receiptFact, action, receipt, 2, "correlation-1", at);

        var facts = ArgumentCaptor.forClass(TimelineMapper.Fact.class);
        var events = ArgumentCaptor.forClass(OutboxMapper.NewEvent.class);
        verify(timelineMapper, org.mockito.Mockito.times(2)).insert(facts.capture());
        verify(outboxMapper, org.mockito.Mockito.times(2)).insert(events.capture());
        for (int i = 0; i < 2; i++) {
            assertEquals(facts.getAllValues().get(i).getId(), events.getAllValues().get(i).getEventId());
            assertEquals(facts.getAllValues().get(i).getBusinessEventKey(),
                    events.getAllValues().get(i).getBusinessEventKey());
            var envelope = new ObjectMapper().readTree(events.getAllValues().get(i).getPayloadJson());
            assertEquals(1, envelope.get("schemaVersion").asInt());
            assertEquals(events.getAllValues().get(i).getEventId(), envelope.get("eventId").asText());
            assertFalse(envelope.toString().contains("secret-parameters-must-not-leak"));
            assertFalse(envelope.toString().contains("ticket"));
        }
        assertTrue(events.getAllValues().get(0).getPayloadJson().contains("incident.action-approval-decided"));
        assertTrue(events.getAllValues().get(1).getPayloadJson().contains("incident.action-receipt-accepted"));
    }
}
