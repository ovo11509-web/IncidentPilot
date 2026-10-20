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

import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.action.service.ActionClosureInvalidator;
import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.timeline.service.TimelineWriter;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Protected lifecycle transitions with an immutable first-resolution seal. */
@Service
public final class IncidentTransitionService {
    private final IncidentMapper incidents;
    private final AlertMapper alerts;
    private final AggregationClockMapper clock;
    private final IncidentStateMachine machine;
    private final TimelineWriter timeline;
    private final TransactionTemplate transactions;
    private ActionClosureInvalidator actionClosureInvalidator;

    public IncidentTransitionService(IncidentMapper incidents, AlertMapper alerts, AggregationClockMapper clock,
            IncidentStateMachine machine, TimelineWriter timeline, PlatformTransactionManager manager) {
        this.incidents = incidents;
        this.alerts = alerts;
        this.clock = clock;
        this.machine = machine;
        this.timeline = timeline;
        this.transactions = new TransactionTemplate(manager);
    }

    @Autowired
    public void setActionClosureInvalidator(ActionClosureInvalidator invalidator) {
        this.actionClosureInvalidator = invalidator;
    }

    public IncidentMapper.IncidentRow transition(String incidentId, String expectedState, long expectedVersion,
            String targetState, String reason, String actorId, String requestId) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.transition", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        var candidate = incidents.findById(incidentId);
        if (candidate == null) throw new NotFoundException();
        observation.id("incidentId", candidate.getId());
        return transactions.execute(status -> transitionLocked(incidentId, candidate.getAggregationKey(),
                expectedState, expectedVersion, targetState, reason, actorId, requestId));

        }
    }

    private IncidentMapper.IncidentRow transitionLocked(String id, String key, String expectedState,
            long expectedVersion, String targetState, String reason, String actorId, String requestId) {
        clock.lockKey(key);
        var current = incidents.lockById(id);
        if (current == null) throw new NotFoundException();
        if (!current.getLifecycleState().equals(expectedState) || current.getVersion() != expectedVersion)
            throw new ConflictException(current);
        machine.requireAllowed(current.getLifecycleState(), targetState, reason, "USER", actorId);
        if ("RESOLVED".equals(current.getLifecycleState()) && "ACKNOWLEDGED".equals(targetState)
                && incidents.hasNewerUnclosed(key, current.getEpisodeStartAt(), current.getEpisodeAnchorAlertId()))
            throw new ConflictException(current);
        boolean firstSeal = "RESOLVED".equals(targetState) && current.getLifecycleSealedAt() == null;
        var sealedAt = firstSeal ? clock.allocateSealedAt(key) : null;
        int factCount = firstSeal ? 2 : 1;
        if (incidents.transition(id, expectedState, expectedVersion, targetState, sealedAt, factCount) != 1)
            throw new ConflictException(incidents.findById(id));
        var anchor = alerts.findById(current.getEpisodeAnchorAlertId());
        if (anchor == null) throw new IllegalStateException("Incident anchor Alert missing");
        var occurredAt = firstSeal ? sealedAt : incidents.databaseNow();
        timeline.statusChanged(id, current.getTimelineSeq() + 1, expectedState, targetState, reason,
                actorId, requestId, anchor.getCorrelationId(), occurredAt);
        if (firstSeal) timeline.aggregationSealed(id, current.getTimelineSeq() + 2,
                anchor.getCorrelationId(), sealedAt);
        if ("CLOSED".equals(targetState) && actionClosureInvalidator != null) {
            actionClosureInvalidator.invalidate(id, anchor.getCorrelationId(), occurredAt);
        }
        return incidents.findById(id);
    }

    public static final class NotFoundException extends RuntimeException {}
    public static final class ConflictException extends RuntimeException {
        private final IncidentMapper.IncidentRow current;
        public ConflictException(IncidentMapper.IncidentRow current) { this.current = current; }
        public IncidentMapper.IncidentRow current() { return current; }
    }
}
