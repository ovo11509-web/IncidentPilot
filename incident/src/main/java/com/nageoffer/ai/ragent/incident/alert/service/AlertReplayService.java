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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertAuditMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Replays a FAILED Alert in place and appends accepted or rejected audit facts. */
@Service
public final class AlertReplayService {
    private final AlertMapper alerts;
    private final AlertAuditMapper audit;
    private final TransactionTemplate transactions;
    private final ObjectMapper json = new ObjectMapper();

    public AlertReplayService(AlertMapper alerts, AlertAuditMapper audit, PlatformTransactionManager manager) {
        this.alerts = alerts;
        this.audit = audit;
        this.transactions = new TransactionTemplate(manager);
    }

    public AlertMapper.AlertRow replay(String alertId, long expectedVersion, String reason,
            String actorId, String requestId) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.alert.replay", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        String normalized = Normalizer.normalize(reason == null ? "" : reason, Normalizer.Form.NFC).strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 512
                || normalized.codePoints().anyMatch(Character::isISOControl)
                || actorId == null || actorId.isBlank()) throw new InvalidReplayException();
        var result = transactions.execute(status -> {
            var current = alerts.lockById(alertId);
            if (current == null) throw new NotFoundException();
            boolean accepted = "FAILED".equals(current.getProcessingStatus())
                    && current.getVersion() == expectedVersion;
            if (accepted && alerts.replayFailed(alertId, expectedVersion) != 1)
                throw new IllegalStateException("Replay state changed");
            var fact = new AlertAuditMapper.Fact();
            fact.setId(IdUtil.getSnowflakeNextIdStr());
            fact.setAlertId(alertId);
            fact.setFactKey(accepted
                    ? "alert:" + alertId + ":epoch:" + (current.getProcessingEpoch() + 1) + ":replay-accepted"
                    : "alert:" + alertId + ":replay-rejected:" + requestId);
            fact.setFactType(accepted ? "ALERT_REPLAY_ACCEPTED" : "ALERT_REPLAY_REJECTED");
            fact.setActorType("USER");
            fact.setActorId(actorId);
            fact.setRequestId(requestId);
            fact.setCorrelationId(current.getCorrelationId());
            fact.setProcessingEpoch(accepted ? current.getProcessingEpoch() + 1 : current.getProcessingEpoch());
            fact.setReasonCode("MANUAL_REPLAY");
            fact.setFailureCode(current.getFailureCode());
            fact.setMetadataJson(metadata(normalized, current.getFailureCode()));
            audit.insert(fact);
            return new Outcome(accepted, accepted ? alerts.findById(alertId) : current);
        });
        com.nageoffer.ai.ragent.incident.observability.IncidentObservation.alert(result.row());
        observation.outcome(result.accepted() ? "REPLAY_ACCEPTED" : "REPLAY_REJECTED");
        if (!result.accepted()) throw new ConflictException(result.row());
        return result.row();

        }
    }

    private String metadata(String reason, String failureCode) {
        try {
            return json.writeValueAsString(Map.of("reasonDigest", digest(reason),
                    "priorFailureCode", failureCode == null ? "NONE" : failureCode));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Safe replay metadata encoding failed", e);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record Outcome(boolean accepted, AlertMapper.AlertRow row) {}
    public static final class InvalidReplayException extends RuntimeException {}
    public static final class NotFoundException extends RuntimeException {}
    public static final class ConflictException extends RuntimeException {
        private final AlertMapper.AlertRow current;
        public ConflictException(AlertMapper.AlertRow current) { this.current = current; }
        public AlertMapper.AlertRow current() { return current; }
    }
}
