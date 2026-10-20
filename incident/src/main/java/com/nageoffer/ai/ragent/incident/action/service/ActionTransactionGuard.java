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

package com.nageoffer.ai.ragent.incident.action.service;

import com.nageoffer.ai.ragent.incident.core.dao.AggregationClockMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import java.time.OffsetDateTime;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared lock order: aggregation key, Incident row, then Action rows. Callback must not perform network I/O. */
@Service
public final class ActionTransactionGuard {
    private final IncidentMapper incidents;
    private final AggregationClockMapper clock;
    private final TransactionTemplate transactions;

    public ActionTransactionGuard(IncidentMapper incidents, AggregationClockMapper clock,
            PlatformTransactionManager manager) {
        this.incidents = incidents;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
    }

    public <T> T inIncident(String incidentId, Function<LockedIncident, T> operation) {
        var candidate = incidents.findById(incidentId);
        if (candidate == null) {
            throw new IllegalArgumentException("Incident not found");
        }
        return transactions.execute(status -> {
            clock.lockKey(candidate.getAggregationKey());
            var current = incidents.lockById(incidentId);
            if (current == null || !current.getAggregationKey().equals(candidate.getAggregationKey())) {
                throw new IllegalStateException("Incident changed while acquiring lock");
            }
            return operation.apply(new LockedIncident(current));
        });
    }

    public final class LockedIncident {
        private final IncidentMapper.IncidentRow row;

        private LockedIncident(IncidentMapper.IncidentRow row) {
            this.row = row;
        }

        public IncidentMapper.IncidentRow row() {
            return row;
        }

        /** Re-read the database clock after all relevant row locks, never transaction-start CURRENT_TIMESTAMP. */
        public OffsetDateTime now() {
            return incidents.databaseNow();
        }

        public long reserveTimeline(int facts) {
            if (facts < 1 || incidents.reserveTimeline(row.getId(), row.getTimelineSeq(), row.getVersion(), facts) != 1) {
                throw new IllegalStateException("Timeline sequence conflict");
            }
            long first = row.getTimelineSeq() + 1;
            row.setTimelineSeq(row.getTimelineSeq() + facts);
            return first;
        }
    }
}
