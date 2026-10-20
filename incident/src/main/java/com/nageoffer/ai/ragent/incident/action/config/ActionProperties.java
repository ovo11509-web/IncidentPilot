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

package com.nageoffer.ai.ragent.incident.action.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "incident.action")
public class ActionProperties {
    private Duration approvalTtl = Duration.ofMinutes(15);
    private Duration authorizationTtl = Duration.ofMinutes(5);
    private Duration ticketTtl = Duration.ofMinutes(2);
    private Duration maxExecutionDuration = Duration.ofMinutes(2);
    private Duration receiptGracePeriod = Duration.ofMinutes(3);
    private int preConsumptionRetries = 3;
    /** Explicitly registered isolated simulation IDs. Empty means no proposal can be created. */
    private Set<String> allowedSimulationTargets = Set.of();
    private Worker worker = new Worker();

    @PostConstruct
    public void validate() {
        requirePositive(approvalTtl, "approvalTtl");
        requirePositive(authorizationTtl, "authorizationTtl");
        requirePositive(ticketTtl, "ticketTtl");
        requirePositive(maxExecutionDuration, "maxExecutionDuration");
        requirePositive(receiptGracePeriod, "receiptGracePeriod");
        if (maxExecutionDuration.compareTo(Duration.ofMillis(1)) < 0
                || receiptGracePeriod.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException("execution durations must be at least 1 ms");
        }
        if (ticketTtl.compareTo(authorizationTtl) > 0) {
            throw new IllegalArgumentException("ticketTtl exceeds authorizationTtl");
        }
        if (preConsumptionRetries < 0 || preConsumptionRetries > 3) {
            throw new IllegalArgumentException("preConsumptionRetries must be between 0 and 3");
        }
        if (worker == null) {
            throw new IllegalArgumentException("worker is required");
        }
        if (allowedSimulationTargets == null || allowedSimulationTargets.stream()
                .anyMatch(id -> id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))) {
            throw new IllegalArgumentException("invalid allowedSimulationTargets");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    @Data
    public static class Worker {
        private boolean enabled;
    }
}
