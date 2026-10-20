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

package com.nageoffer.ai.ragent.incident.core.service;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertAuditMapper;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.outbox.service.IncidentEventWriter;
import com.nageoffer.ai.ragent.incident.timeline.service.TimelineWriter;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Deterministic episode materialization under the same key lock used by receipt and sealing. */
@Service
public final class IncidentAggregationService {
    private final AlertMapper alerts;
    private final AlertAuditMapper audit;
    private final IncidentMapper incidents;
    private final AggregationClockMapper clock;
    private final EpisodePartitioner partitioner;
    private final TimelineWriter timeline;
    private final IncidentEventWriter events;
    private final IncidentProperties properties;
    private final TransactionTemplate transactions;

    public IncidentAggregationService(AlertMapper alerts, AlertAuditMapper audit, IncidentMapper incidents,
            AggregationClockMapper clock,
            EpisodePartitioner partitioner, TimelineWriter timeline, IncidentEventWriter events,
            IncidentProperties properties, PlatformTransactionManager manager) {
        this.alerts = alerts;
        this.audit = audit;
        this.incidents = incidents;
        this.clock = clock;
        this.partitioner = partitioner;
        this.timeline = timeline;
        this.events = events;
        this.properties = properties;
        this.transactions = new TransactionTemplate(manager);
    }

    public boolean processOne(String alertId) {
        try (var scope = com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.alert.process")) {
            boolean result = processOneObserved(alertId);
            // Evidence lookup is auxiliary and must not change the committed processing result.
            try { com.nageoffer.ai.ragent.incident.observability.IncidentObservation.alert(alerts.findById(alertId)); }
            catch (RuntimeException ignored) { }
            scope.outcome(result ? "PROCESSED" : "NO_CHANGE");
            return result;
        }
    }

    private boolean processOneObserved(String alertId) {
        var candidate = alerts.findById(alertId);
        if (candidate == null || !"RECEIVED".equals(candidate.getProcessingStatus())) return false;
        return Boolean.TRUE.equals(transactions.execute(status -> processLocked(alertId, candidate.getAggregationKey())));
    }

    private boolean processLocked(String alertId, String key) {
        clock.lockKey(key);
        var target = alerts.lockDueById(alertId);
        if (target == null || !key.equals(target.getAggregationKey())) return false;
        var members = alerts.findAllByAggregationKey(key);
        var existing = incidents.findAllByAggregationKey(key);
        var points = members.stream().map(row -> new EpisodePartitioner.AlertPoint(
                row.getId(), row.getReceivedAt(), row.getProcessingStatus())).toList();
        var seals = existing.stream().map(IncidentMapper.IncidentRow::getLifecycleSealedAt)
                .filter(java.util.Objects::nonNull).toList();
        EpisodePartitioner.Episode episode = partitioner.partition(points, seals,
                properties.getAggregation().getWindow()).stream()
                .filter(value -> value.members().stream().anyMatch(point -> point.id().equals(alertId)))
                .findFirst().orElseThrow(() -> new IllegalStateException("Persisted alert was not partitioned"));
        var incident = incidents.lockByEpisode(key, episode.anchor().id());
        String eventId = IdUtil.getSnowflakeNextIdStr();
        OffsetDateTime now = incidents.databaseNow();
        if (incident == null) {
            createAndAttach(target, episode, eventId, now);
        } else {
            attachExisting(target, incident, eventId, now);
        }
        if (target.getProcessingEpoch() > 0) {
            var fact = new AlertAuditMapper.Fact();
            fact.setId(IdUtil.getSnowflakeNextIdStr());
            fact.setAlertId(alertId);
            fact.setFactKey("alert:" + alertId + ":epoch:" + target.getProcessingEpoch() + ":recovered");
            fact.setFactType("ALERT_RECOVERED");
            fact.setActorType("SYSTEM");
            fact.setActorId("incident-worker");
            fact.setCorrelationId(target.getCorrelationId());
            fact.setProcessingEpoch(target.getProcessingEpoch());
            audit.insert(fact);
        }
        return true;
    }

    private void createAndAttach(AlertMapper.AlertRow target, EpisodePartitioner.Episode episode,
            String attachmentEventId, OffsetDateTime now) {
        String key = target.getAggregationKey();
        var active = incidents.lockActiveByKey(key);
        boolean newerActive = active != null && active.getEpisodeStartAt().isAfter(episode.anchor().receivedAt());
        if (active != null && !newerActive && incidents.closeExpiredSlot(active.getId(), active.getVersion()) != 1)
            throw new IllegalStateException("Active episode changed during aggregation");
        OffsetDateTime latest = episode.members().get(episode.members().size() - 1).receivedAt();
        boolean bornSealed = newerActive || !now.isBefore(latest.plus(properties.getAggregation().getWindow()));
        var created = new IncidentMapper.NewIncident();
        created.setId(IdUtil.getSnowflakeNextIdStr());
        created.setAggregationKey(key);
        created.setFingerprint(target.getFingerprint());
        created.setAggregationRuleVersion(properties.getAggregation().getRuleVersion());
        created.setAggregationWindowSeconds(Math.toIntExact(properties.getAggregation().getWindow().toSeconds()));
        created.setEpisodeStartAt(episode.anchor().receivedAt());
        created.setEpisodeAnchorAlertId(episode.anchor().id());
        created.setFirstAlertReceivedAt(target.getReceivedAt());
        created.setLastAlertReceivedAt(target.getReceivedAt());
        created.setAlertCount(1);
        created.setAggregationSlotActive(!bornSealed);
        created.setBornSealed(bornSealed);
        created.setCreatedEventId(IdUtil.getSnowflakeNextIdStr());
        incidents.insert(created);
        if (alerts.attachOnce(target.getId(), created.getId(), attachmentEventId) != 1)
            throw new IllegalStateException("Alert changed during first attachment");
        timeline.created(created, target.getCorrelationId(), now);
        timeline.attached(created.getId(), 2, target, attachmentEventId, false, now);
        events.created(created, target.getCorrelationId(), 1, now);
        events.attached(created.getId(), target, attachmentEventId, 1, false, now);
    }

    private void attachExisting(AlertMapper.AlertRow target, IncidentMapper.IncidentRow incident,
            String attachmentEventId, OffsetDateTime now) {
        if (alerts.attachOnce(target.getId(), incident.getId(), attachmentEventId) != 1)
            throw new IllegalStateException("Alert changed during attachment");
        if (incidents.addAttachedAlert(incident.getId(), incident.getVersion(), target.getReceivedAt()) != 1)
            throw new IllegalStateException("Incident changed during attachment");
        boolean late = incident.getLifecycleSealedAt() != null;
        timeline.attached(incident.getId(), incident.getTimelineSeq() + 1, target, attachmentEventId, late, now);
        events.attached(incident.getId(), target, attachmentEventId, incident.getVersion() + 1, late, now);
    }
}
