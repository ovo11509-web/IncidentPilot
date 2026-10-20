package com.nageoffer.ai.ragent.incident.action.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.action.dao.ExecutionMapper;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.worker.ActionDispatchWorker;
import com.nageoffer.ai.ragent.incident.outbox.worker.OutboxRelay;
import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ActionOutboxCompatibilityTest {
    @Test
    void realRelayRecoversBothActionEventsAfterSendAndPublishMarkFailures() throws Exception {
        var relaySpans = new java.util.ArrayList<io.opentelemetry.sdk.trace.data.SpanData>();
        var exporter = new io.opentelemetry.sdk.trace.export.SpanExporter() {
            public io.opentelemetry.sdk.common.CompletableResultCode export(java.util.Collection<io.opentelemetry.sdk.trace.data.SpanData> data) { relaySpans.addAll(data); return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess(); }
            public io.opentelemetry.sdk.common.CompletableResultCode flush() { return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess(); }
            public io.opentelemetry.sdk.common.CompletableResultCode shutdown() { return io.opentelemetry.sdk.common.CompletableResultCode.ofSuccess(); }
        };
        try (var provider = io.opentelemetry.sdk.trace.SdkTracerProvider.builder().addSpanProcessor(io.opentelemetry.sdk.trace.export.SimpleSpanProcessor.create(exporter)).build();
             var db = new ActionDbFixture(); var stp = Mockito.mockStatic(StpUtil.class)) {
            com.nageoffer.ai.ragent.framework.observability.ObservationScope.configure(io.opentelemetry.sdk.OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var claim = new ActionDispatchWorker(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, db.properties).claim("200");
            var auth = mock(ActionServiceAuthentication.class);
            when(auth.isAuthenticated("Bearer callback")).thenReturn(true);
            var consumption = new ActionTicketConsumptionService(db.actions, db.approvals, db.executions,
                    db.tickets, db.guard, db.actionTimeline, db.alerts, auth);
            assertEquals("GRANTED", consumption.consume(new ActionTicketConsumptionService.ConsumeRequest(
                    claim.ticket(), claim.actionId(), claim.incidentId(), claim.executionId(), claim.executionKey(),
                    claim.requestId(), claim.bindingVersion(), claim.toolName(), claim.toolVersion(), "SIM_SERVICE",
                    claim.targetId(), claim.parametersJson()), "Bearer callback").decision());
            var receipts = new ActionReceiptService(db.actions, db.executions, db.receipts, db.guard,
                    db.actionTimeline, db.actionOutbox, db.alerts, auth);
            var receipt = new ActionReceiptService.Submission(1, "400", "200", "100", claim.executionId(),
                    claim.executionKey(), claim.requestId(), "simulation.set-state", "1", "SIM_SERVICE", "demo",
                    "SUCCESS", "SIMULATION_APPLIED", OffsetDateTime.now(),
                    "{\"desiredState\":\"PAUSED\",\"applied\":true}");
            assertEquals("APPLIED", receipts.accept(receipt, "Bearer callback").disposition());
            var facts = db.timeline.findByIncidentId("100");
            var events = facts.stream().map(fact -> db.outbox.findById(fact.getId()))
                    .filter(java.util.Objects::nonNull).toList();
            assertEquals(2, events.size());
            assertEquals(java.util.Set.of("incident.action-approval-decided", "incident.action-receipt-accepted"),
                    events.stream().map(event -> event.getEventType()).collect(java.util.stream.Collectors.toSet()));
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            var calls = new java.util.ArrayList<java.util.Map<String, Object>>();
            var failSend = new java.util.concurrent.atomic.AtomicBoolean(true);
            var producer = mock(MessageQueueProducer.class);
            when(producer.send(Mockito.eq("incident-domain-events"), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                var envelope = (java.util.Map<String, Object>) invocation.getArgument(3);
                String eventId = invocation.getArgument(1);
                var original = events.stream().filter(event -> eventId.equals(event.getEventId())).findFirst().orElseThrow();
                assertEquals(eventId, envelope.get("eventId"));
                assertEquals(json.readTree(original.getPayloadJson()), json.valueToTree(envelope));
                calls.add(new java.util.LinkedHashMap<>(envelope));
                if (failSend.get()) throw new IllegalStateException("test producer unavailable");
                var sent = mock(org.apache.rocketmq.client.producer.SendResult.class);
                when(sent.getSendStatus()).thenReturn(org.apache.rocketmq.client.producer.SendStatus.SEND_OK);
                when(sent.getMsgId()).thenReturn("test-broker-id");
                return sent;
            });
            var relay = new OutboxRelay(db.outbox, producer, db.incidents, db.incidentProperties, db.transactions);
            assertEquals(0, relay.publishDue());
            assertEquals(2, calls.size());
            for (var event : events) assertEquals("PENDING", db.outbox.findById(event.getEventId()).getPublishStatus());
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            assertEquals(facts.size(), db.timeline.findByIncidentId("100").size());
            db.sql("CREATE FUNCTION fail_action_publish_mark() RETURNS trigger LANGUAGE plpgsql AS $$ "
                    + "BEGIN IF NEW.publish_status='PUBLISHED' THEN RAISE EXCEPTION 'test publish mark failure'; "
                    + "END IF; RETURN NEW; END $$");
            db.sql("CREATE TRIGGER fail_action_publish_mark BEFORE UPDATE ON t_incident_outbox "
                    + "FOR EACH ROW EXECUTE FUNCTION fail_action_publish_mark()");
            failSend.set(false);
            db.sql("UPDATE t_incident_outbox SET next_attempt_at=clock_timestamp()-interval '1 second'");
            assertEquals(0, relay.publishDue());
            assertEquals(4, calls.size());
            for (var event : events) assertEquals("PENDING", db.outbox.findById(event.getEventId()).getPublishStatus());
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            assertEquals("APPROVED", db.approvals.findByActionId("200").getStatus());
            assertEquals(1, db.receipts.findByExecutionId(claim.executionId()).size());
            assertEquals(facts.size(), db.timeline.findByIncidentId("100").size());
            db.sql("DROP TRIGGER fail_action_publish_mark ON t_incident_outbox");
            db.sql("UPDATE t_incident_outbox SET next_attempt_at=clock_timestamp()-interval '1 second'");
            assertEquals(2, relay.publishDue());
            assertEquals(6, calls.size());
            var attempts = relaySpans.stream().filter(span -> span.getName().equals("incident.outbox.publish")).toList();
            assertEquals(6, attempts.size());
            assertEquals(6, attempts.stream().map(io.opentelemetry.sdk.trace.data.SpanData::getTraceId).distinct().count());
            for (var event : events) {
                var actual = attempts.stream().filter(span -> event.getEventId().equals(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("ragent.event_id")))).toList();
                assertEquals(3, actual.size());
                assertEquals(java.util.Set.of("1", "2", "3"), actual.stream().map(span -> span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("ragent.attempt"))).collect(java.util.stream.Collectors.toSet()));
                assertTrue(actual.stream().allMatch(span -> "100".equals(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("ragent.incident_id")))));
            }
            assertTrue(!relaySpans.toString().contains(claim.ticket()));
            assertEquals(0, relay.publishDue());
            assertEquals("APPLIED", receipts.accept(receipt, "Bearer callback").disposition());
            assertEquals(facts.size(), db.timeline.findByIncidentId("100").size());
            var proof = java.nio.file.Path.of("target", "safety-closeout", "action-relay-" + java.util.UUID.randomUUID());
            java.nio.file.Files.createDirectories(proof);
            var result = json.createObjectNode().put("scenario", "B11-action-relay")
                    .put("actionId", "200").put("executionId", claim.executionId()).put("finalState", "SUCCEEDED")
                    .put("approvalState", "APPROVED").put("receipts", 1).put("timelineFacts", facts.size())
                    .put("producerAttempts", calls.size()).put("publishedEvents", 2)
                    .put("realRelay", true).put("realDatabasePublishFailure", true)
                    .put("realBroker", false).put("realToolInvoked", false);
            var published = result.putArray("events");
            for (var event : events) {
                var after = db.outbox.findById(event.getEventId());
                assertEquals("PUBLISHED", after.getPublishStatus());
                assertEquals(3, after.getAttemptCount());
                assertEquals(event.getPayloadJson(), after.getPayloadJson());
                assertEquals(3, calls.stream().filter(call -> event.getEventId().equals(call.get("eventId"))).count());
                published.addObject().put("eventId", event.getEventId()).put("eventType", event.getEventType())
                        .put("businessEventKey", event.getBusinessEventKey()).put("attemptCount", after.getAttemptCount())
                        .put("publishStatus", after.getPublishStatus()).set("envelope", json.readTree(after.getPayloadJson()));
            }
            String safeEvidence = json.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            assertTrue(!safeEvidence.contains(claim.ticket()));
            java.nio.file.Files.writeString(proof.resolve("summary.json"), safeEvidence);
            java.nio.file.Files.writeString(proof.resolve("producer-attempts.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(calls));
        } finally { com.nageoffer.ai.ragent.framework.observability.ObservationScope.configure(null); }
    }

    @Test
    void approvalAndAuthoritativeReceiptShareStableTimelineAndOutboxIdentityAcrossRetry() throws Exception {
        try (var db = new ActionDbFixture(); var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            db.seedIncident("100");
            db.seedPendingAction("200", OffsetDateTime.now().plusMinutes(10));
            new ActionDecisionService(db.actions, db.approvals, db.guard, db.actionTimeline,
                    db.actionOutbox, db.properties, db.alerts).decide("200", 0, "APPROVE", null, "99");
            var approvalFact = db.timeline.findByIncidentId("100").get(0);
            var approvalEvent = db.outbox.findById(approvalFact.getId());
            assertNotNull(approvalEvent);
            assertEquals("incident.action-approval-decided", approvalEvent.getEventType());
            assertEquals(approvalFact.getBusinessEventKey(), approvalEvent.getBusinessEventKey());
            assertEquals("PENDING", approvalEvent.getPublishStatus());

            var action = db.actions.findById("200");
            var execution = new ExecutionMapper.NewExecution();
            execution.setId("300"); execution.setActionId("200");
            execution.setExecutionKey(action.getExecutionKey());
            execution.setDispatchRequestId("dispatch-300");
            execution.setToolName(action.getToolName());
            execution.setToolVersion(action.getToolVersion());
            execution.setTargetType(action.getTargetType());
            execution.setTargetId(action.getTargetId());
            execution.setBindingDigest(action.getBindingDigest());
            execution.setMaxExecutionDurationMs(120000);
            execution.setReceiptGracePeriodMs(180000);
            db.executions.insert(execution);
            db.executions.markStarted("300", OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(5));
            db.sql("UPDATE t_incident_action SET state='EXECUTING',version=3 WHERE id='200'");
            var auth = mock(ActionServiceAuthentication.class);
            when(auth.isAuthenticated("Bearer callback")).thenReturn(true);
            var service = new ActionReceiptService(db.actions, db.executions, db.receipts, db.guard,
                    db.actionTimeline, db.actionOutbox, db.alerts, auth);
            var receipt = new ActionReceiptService.Submission(1, "400", "200", "100", "300",
                    action.getExecutionKey(), "dispatch-300", "simulation.set-state", "1",
                    "SIM_SERVICE", "demo", "SUCCESS", "SIMULATION_APPLIED", OffsetDateTime.now(),
                    "{\"desiredState\":\"PAUSED\",\"applied\":true}");
            assertEquals("APPLIED", service.accept(receipt, "Bearer callback").disposition());
            assertEquals("APPLIED", service.accept(receipt, "Bearer callback").disposition());
            var facts = db.timeline.findByIncidentId("100");
            var receiptFact = facts.stream().filter(f -> "ACTION_RECEIPT_ACCEPTED".equals(f.getEventType()))
                    .findFirst().orElseThrow();
            var receiptEvent = db.outbox.findById(receiptFact.getId());
            assertEquals("incident.action-receipt-accepted", receiptEvent.getEventType());
            assertEquals(receiptFact.getBusinessEventKey(), receiptEvent.getBusinessEventKey());
            assertEquals(1, db.receipts.findByExecutionId("300").size());

            assertEquals(2, db.outbox.claimDue("attempt-1", 30, 10));
            var original = db.outbox.findClaimed("attempt-1").stream()
                    .filter(e -> e.getEventId().equals(receiptEvent.getEventId())).findFirst().orElseThrow();
            db.outbox.reschedule(receiptEvent.getEventId(), "attempt-1", OffsetDateTime.now().minusSeconds(1),
                    "BROKER_UNAVAILABLE", "retry");
            assertEquals(1, db.outbox.claimDue("attempt-2", 30, 10));
            var replay = db.outbox.findClaimed("attempt-2").get(0);
            assertEquals(original.getEventId(), replay.getEventId());
            assertEquals(original.getPayloadJson(), replay.getPayloadJson());
            assertEquals("SUCCEEDED", db.actions.findById("200").getState());
            assertTrue(db.timeline.findByIncidentId("100").stream()
                    .filter(f -> "ACTION_RECEIPT_ACCEPTED".equals(f.getEventType())).count() == 1);
        }
    }
}
