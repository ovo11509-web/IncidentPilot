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

package com.nageoffer.ai.ragent.incident.alert.service;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer.CanonicalAlert;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertAuditMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import java.sql.SQLException;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Only a committed Alert and its first audit fact count as an accepted submission. */
@Service
public final class AlertReceiveService {
    private final AlertMapper alerts;
    private final AlertAuditMapper audit;
    private final AggregationClockMapper clock;
    private final AlertFingerprint fingerprints;
    private final TransactionTemplate transactions;

    public AlertReceiveService(AlertMapper alerts, AlertAuditMapper audit, AggregationClockMapper clock,
            AlertFingerprint fingerprints, PlatformTransactionManager manager) {
        this.alerts = alerts;
        this.audit = audit;
        this.clock = clock;
        this.fingerprints = fingerprints;
        this.transactions = new TransactionTemplate(manager);
    }

    public Submission receive(String sourceId, CanonicalAlert canonical, String requestId) {
        try (var scope = com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.alert.receive")) {
            var result = receiveObserved(sourceId, canonical, requestId);
            scope.id("requestId", requestId).id("alertId", result.alertId()).id("correlationId", result.correlationId())
                    .id("incidentId", result.incidentId()).outcome(result.duplicate() ? "REUSED" : "RECEIVED");
            return result;
        }
    }

    private Submission receiveObserved(String sourceId, CanonicalAlert canonical, String requestId) {
        try {
            AlertMapper.AlertRow existing = alerts.findByIdentity(sourceId, canonical.externalAlertId());
            if (existing != null) return duplicate(existing, canonical.payloadDigest());
            var fingerprint = fingerprints.of(sourceId, canonical);
            try {
                return transactions.execute(status -> {
                    var alert = new AlertMapper.NewAlert();
                    alert.setId(IdUtil.getSnowflakeNextIdStr());
                    alert.setCorrelationId(IdUtil.getSnowflakeNextIdStr());
                    alert.setSourceId(sourceId);
                    alert.setExternalAlertId(canonical.externalAlertId());
                    alert.setPayloadJson(canonical.payloadJson());
                    alert.setPayloadDigest(canonical.payloadDigest());
                    alert.setOccurredAt(canonical.occurredAt());
                    alert.setRuleId(canonical.ruleId());
                    alert.setTitle(canonical.title());
                    alert.setSeverity(canonical.severity());
                    alert.setEnvironment(canonical.environment());
                    alert.setService(canonical.service());
                    alert.setResourceType(canonical.resourceType());
                    alert.setResourceId(canonical.resourceId());
                    alert.setSummary(canonical.summary());
                    alert.setLabelsJson(serializeLabels(canonical));
                    alert.setFingerprint(fingerprint.digest());
                    alert.setAggregationKey(fingerprint.aggregationKey());
                    alert.setReceivedAt(clock.allocateReceivedAt(fingerprint.aggregationKey()));
                    alerts.insert(alert);
                    var fact = new AlertAuditMapper.Fact();
                    fact.setId(IdUtil.getSnowflakeNextIdStr());
                    fact.setAlertId(alert.getId());
                    fact.setFactKey("alert:" + alert.getId() + ":received");
                    fact.setFactType("ALERT_RECEIVED");
                    fact.setActorType("SOURCE");
                    fact.setActorId(sourceId);
                    fact.setRequestId(requestId);
                    fact.setCorrelationId(alert.getCorrelationId());
                    audit.insert(fact);
                    return new Submission(alert.getId(), alert.getCorrelationId(), "RECEIVED", false,
                            false, null, null);
                });
            } catch (DataIntegrityViolationException e) {
                if (!identityConflict(e)) throw new PersistenceUnavailableException();
                AlertMapper.AlertRow winner = alerts.findByIdentity(sourceId, canonical.externalAlertId());
                if (winner == null) throw new PersistenceUnavailableException();
                return duplicate(winner, canonical.payloadDigest());
            }
        } catch (ConflictException | PersistenceUnavailableException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new PersistenceUnavailableException();
        }
    }

    private static String serializeLabels(CanonicalAlert canonical) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(canonical.labels());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Canonical labels could not be serialized", e);
        }
    }

    private static boolean identityConflict(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof PSQLException pg && pg.getServerErrorMessage() != null
                    && "uk_incident_alert_identity".equals(pg.getServerErrorMessage().getConstraint())) return true;
            if (cause instanceof SQLException sql && !"23505".equals(sql.getSQLState())) return false;
        }
        return false;
    }

    private static Submission duplicate(AlertMapper.AlertRow row, String digest) {
        if (!row.getPayloadDigest().equals(digest)) throw new ConflictException(row);
        return new Submission(row.getId(), row.getCorrelationId(), row.getProcessingStatus(), true,
                "FAILED".equals(row.getProcessingStatus()), row.getIncidentId(), row.getFailureCode());
    }

    public record Submission(String alertId, String correlationId, String processingStatus,
            boolean duplicate, boolean replayEligible, String incidentId, String failureCode) {}

    public static final class ConflictException extends RuntimeException {
        private final AlertMapper.AlertRow existing;
        public ConflictException(AlertMapper.AlertRow existing) { this.existing = existing; }
        public AlertMapper.AlertRow existing() { return existing; }
    }
    public static final class PersistenceUnavailableException extends RuntimeException {}
}
