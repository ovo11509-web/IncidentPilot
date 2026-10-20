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

import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import com.nageoffer.ai.ragent.incident.core.service.IncidentAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A database-backed scanner; every candidate is rechecked under the key lock. */
@Component
@RequiredArgsConstructor
public final class AlertWorker {
    private final AlertMapper alerts;
    private final IncidentAggregationService aggregation;
    private final IncidentProperties properties;
    private final AlertRetryPolicy retryPolicy;

    @Scheduled(fixedDelayString = "${incident.worker.poll-delay-ms:1000}")
    public void processDue() {
        if (!properties.getWorker().isEnabled()) return;
        for (String id : alerts.findDueIds(64)) {
            try (var observation = com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.alert.process")) {
            try {
                observation.outcome(aggregation.processOne(id) ? "PROCESSED" : "NO_CHANGE");
            } catch (RuntimeException failure) {
                retryPolicy.afterFailure(id); observation.outcome("RETRY");
            }
            try { com.nageoffer.ai.ragent.incident.observability.IncidentObservation.alert(alerts.findById(id)); }
            catch (RuntimeException ignored) { }
            }
        }
    }
}
