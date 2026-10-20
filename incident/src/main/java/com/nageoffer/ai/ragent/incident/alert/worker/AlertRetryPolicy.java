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

package com.nageoffer.ai.ragent.incident.alert.worker;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertAuditMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Five configured automatic retries after the first failed processing attempt. */
@Component
public final class AlertRetryPolicy {
    private final AlertMapper alerts;
    private final AlertAuditMapper audit;
    private final IncidentMapper incidents;
    private final IncidentProperties properties;
    private final TransactionTemplate transactions;

    public AlertRetryPolicy(AlertMapper alerts, AlertAuditMapper audit, IncidentMapper incidents,
            IncidentProperties properties, PlatformTransactionManager manager) {
        this.alerts = alerts;
        this.audit = audit;
        this.incidents = incidents;
        this.properties = properties;
        this.transactions = new TransactionTemplate(manager);
    }

    public Outcome afterFailure(String alertId) {
        return transactions.execute(status -> {
            var row = alerts.lockDueById(alertId);
            if (row == null) return Outcome.IGNORED;
            var delays = properties.getRetry().getDelays();
            if (row.getRetryCount() < delays.size()) {
                Duration base = delays.get(row.getRetryCount());
                if (base.isNegative()) throw new IllegalStateException("Negative retry delay");
                long bound = Math.max(1, base.toMillis() / 10 + 1);
                long jitter = ThreadLocalRandom.current().nextLong(bound);
                var next = incidents.databaseNow().plus(base).plusNanos(jitter * 1_000_000L);
                if (alerts.scheduleRetry(alertId, row.getVersion(), next) != 1)
                    throw new IllegalStateException("Retry state changed");
                return Outcome.RETRY_SCHEDULED;
            }
            if (alerts.failTerminal(alertId, row.getVersion()) != 1)
                throw new IllegalStateException("Terminal failure state changed");
            var fact = new AlertAuditMapper.Fact();
            fact.setId(IdUtil.getSnowflakeNextIdStr());
            fact.setAlertId(alertId);
            fact.setFactKey("alert:" + alertId + ":epoch:" + row.getProcessingEpoch() + ":terminal");
            fact.setFactType("ALERT_TERMINAL_FAILED");
            fact.setActorType("SYSTEM");
            fact.setActorId("incident-worker");
            fact.setCorrelationId(row.getCorrelationId());
            fact.setProcessingEpoch(row.getProcessingEpoch());
            fact.setFailureCode("PROCESSING_FAILED");
            fact.setAttemptCountSnapshot(row.getAttemptCount() + 1);
            audit.insert(fact);
            return Outcome.TERMINAL_FAILED;
        });
    }

    public enum Outcome { RETRY_SCHEDULED, TERMINAL_FAILED, IGNORED }
}
