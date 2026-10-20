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

package com.nageoffer.ai.ragent.incident.core.dao;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Select;

/** Read-only, database-derived operational counters. */
public interface OperationsMapper {
    @Select("""
            SELECT clock_timestamp() AS "observedAt",
              (SELECT count(*) FROM t_incident_alert WHERE processing_status='RECEIVED') AS "receivedBacklogCount",
              (SELECT CASE WHEN min(received_at) IS NULL THEN NULL ELSE
                 greatest(0, extract(epoch FROM clock_timestamp()-min(received_at))::bigint) END
                 FROM t_incident_alert WHERE processing_status='RECEIVED') AS "oldestReceivedAgeSeconds",
              (SELECT count(*) FROM t_incident_alert WHERE processing_status='RECEIVED'
                 AND retry_count > 0 AND next_attempt_at <= clock_timestamp()) AS "dueRetryCount",
              (SELECT count(*) FROM t_incident_alert WHERE processing_status='FAILED') AS "failedAlertCount",
              (SELECT count(*) FROM t_incident_outbox WHERE publish_status='PENDING') AS "pendingOutboxCount",
              (SELECT CASE WHEN min(created_at) IS NULL THEN NULL ELSE
                 greatest(0, extract(epoch FROM clock_timestamp()-min(created_at))::bigint) END
                 FROM t_incident_outbox WHERE publish_status='PENDING') AS "oldestPendingOutboxAgeSeconds",
              (SELECT count(*) FROM t_incident_outbox WHERE publish_status='PENDING'
                 AND claim_token IS NOT NULL AND claim_until <= clock_timestamp()) AS "expiredOutboxClaimCount",
              (SELECT max(updated_at) FROM t_incident_alert WHERE processing_status='PROCESSED') AS "lastWorkerSuccessAt",
              (SELECT max(published_at) FROM t_incident_outbox WHERE publish_status='PUBLISHED') AS "lastPublisherSuccessAt"
            """)
    Snapshot snapshot();

    @Select("""
            SELECT 'ALERT_WORKER' AS component, last_failure_code AS code, updated_at AS "observedAt",
                   id AS "alertId", correlation_id AS "correlationId", NULL::varchar AS "eventId",
                   incident_id AS "incidentId"
            FROM t_incident_alert WHERE last_failure_code IS NOT NULL
            UNION ALL
            SELECT 'OUTBOX_PUBLISHER', last_error_code, updated_at, NULL, correlation_id,
                   event_id, incident_id
            FROM t_incident_outbox WHERE last_error_code IS NOT NULL
            ORDER BY "observedAt" DESC LIMIT 20
            """)
    List<Failure> recentFailures();

    @Data
    class Snapshot {
        private OffsetDateTime observedAt;
        private long receivedBacklogCount;
        private Long oldestReceivedAgeSeconds;
        private long dueRetryCount;
        private long failedAlertCount;
        private long pendingOutboxCount;
        private Long oldestPendingOutboxAgeSeconds;
        private long expiredOutboxClaimCount;
        private OffsetDateTime lastWorkerSuccessAt;
        private OffsetDateTime lastPublisherSuccessAt;
    }

    @Data
    class Failure {
        private String component;
        private String code;
        private OffsetDateTime observedAt;
        private String alertId;
        private String incidentId;
        private String eventId;
        private String correlationId;
    }
}
