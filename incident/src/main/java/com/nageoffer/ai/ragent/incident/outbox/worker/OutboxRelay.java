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

package com.nageoffer.ai.ragent.incident.outbox.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.mq.producer.MessageQueueProducer;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.outbox.dao.OutboxMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.rocketmq.client.producer.SendStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Publishes committed outbox rows; duplicate delivery retains the same event ID. */
@Component
public final class OutboxRelay {
    private static final String TOPIC = "incident-domain-events";
    private final OutboxMapper outbox;
    private final MessageQueueProducer producer;
    private final IncidentMapper incidents;
    private final IncidentProperties properties;
    private final TransactionTemplate transactions;
    private final ObjectMapper json = new ObjectMapper();

    public OutboxRelay(OutboxMapper outbox, MessageQueueProducer producer, IncidentMapper incidents,
            IncidentProperties properties, PlatformTransactionManager manager) {
        this.outbox = outbox;
        this.producer = producer;
        this.incidents = incidents;
        this.properties = properties;
        this.transactions = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${incident.outbox.poll-delay-ms:1000}")
    public int publishDue() {
        if (!properties.getOutbox().isEnabled()) return 0;
        String token = UUID.randomUUID().toString();
        var claimed = transactions.execute(status -> {
            outbox.claimDue(token, 60, 64);
            return outbox.findClaimed(token);
        });
        int published = 0;
        for (var event : claimed) {
            try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.outbox.publish", io.opentelemetry.api.trace.SpanKind.PRODUCER)) {
            observation.id("eventId", event.getEventId()).id("incidentId", event.getIncidentId())
                    .id("correlationId", event.getCorrelationId()).id("attempt", event.getAttemptCount());
            Map<String, Object> envelope;
            try {
                envelope = json.readValue(event.getPayloadJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
                if (envelope == null || !event.getEventId().equals(envelope.get("eventId"))
                        || !Integer.valueOf(1).equals(envelope.get("schemaVersion"))
                        || !event.getEventType().equals(envelope.get("eventType"))) {
                    retry(event, token, "INVALID_ENVELOPE");
                    continue;
                }
            } catch (JsonProcessingException error) {
                retry(event, token, "INVALID_ENVELOPE");
                continue;
            }
            try {
                var result = producer.send(TOPIC, event.getEventId(), "Incident domain event", envelope);
                if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                    retry(event, token, "SEND_NOT_OK");
                    continue;
                }
                int updated = transactions.execute(status ->
                        outbox.markPublished(event.getEventId(), token, result.getMsgId()));
                published += updated; observation.outcome(updated == 1 ? "PUBLISHED" : "NO_CHANGE");
            } catch (RuntimeException error) {
                observation.outcome("RETRY");
                retry(event, token, "PUBLISH_UNAVAILABLE");
            }
            }
        }
        return published;
    }

    private void retry(OutboxMapper.EventRow event, String token, String code) {
        com.nageoffer.ai.ragent.framework.observability.ObservationScope.identify("outcome", "RETRY");
        com.nageoffer.ai.ragent.framework.observability.ObservationScope.identify("reasonCode", code);
        Duration cap = properties.getOutbox().getMaxBackoff();
        if (cap.isNegative() || cap.isZero() || cap.compareTo(Duration.ofMinutes(15)) > 0)
            throw new IllegalStateException("Outbox max backoff must be between zero and 15 minutes");
        int exponent = Math.min(20, Math.max(0, event.getAttemptCount() - 1));
        Duration delay = Duration.ofSeconds(1L << exponent);
        if (delay.compareTo(cap) > 0) delay = cap;
        var next = incidents.databaseNow().plus(delay);
        transactions.executeWithoutResult(status ->
                outbox.reschedule(event.getEventId(), token, next, code, null));
    }
}
