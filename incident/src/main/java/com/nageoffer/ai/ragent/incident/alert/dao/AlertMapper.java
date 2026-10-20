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

package com.nageoffer.ai.ragent.incident.alert.dao;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** SQL operations allowed on mutable Alert work state. */
public interface AlertMapper {

    @Select("""
            <script>
            SELECT id, source_id AS "sourceId", rule_id AS "ruleId", title, severity,
                service, environment, resource_type AS "resourceType", resource_id AS "resourceId",
                occurred_at AS "occurredAt", received_at AS "receivedAt"
            FROM t_incident_alert WHERE incident_id=#{incidentId} AND processing_status='PROCESSED'
            <if test="afterId != null">AND id &gt; #{afterId}</if>
            ORDER BY id ASC LIMIT #{limit}
            </script>
            """)
    List<AlertRow> findOperationsPage(@Param("incidentId") String incidentId,
            @Param("afterId") String afterId, @Param("limit") int limit);

    @Insert("""
            INSERT INTO t_incident_alert (
                id, source_id, external_alert_id, protocol_version, payload_json, payload_digest,
                event_type, occurred_at, rule_id, title, severity, environment, service,
                resource_type, resource_id, summary, labels_json, fingerprint, aggregation_key,
                received_at, correlation_id, processing_status, next_attempt_at, created_at, updated_at
            ) VALUES (
                #{id}, #{sourceId}, #{externalAlertId}, 1, CAST(#{payloadJson} AS jsonb), #{payloadDigest},
                'FIRING', #{occurredAt}, #{ruleId}, #{title}, #{severity}, #{environment}, #{service},
                #{resourceType}, #{resourceId}, #{summary}, CAST(#{labelsJson} AS jsonb), #{fingerprint},
                #{aggregationKey}, #{receivedAt}, #{correlationId}, 'RECEIVED', #{receivedAt},
                #{receivedAt}, #{receivedAt}
            )
            """)
    int insert(NewAlert alert);

    @Select("""
            SELECT id, source_id AS "sourceId", external_alert_id AS "externalAlertId",
                   payload_digest AS "payloadDigest", processing_status AS "processingStatus",
                   aggregation_key AS "aggregationKey", fingerprint,
                   received_at AS "receivedAt",
                   processing_epoch AS "processingEpoch", incident_id AS "incidentId",
                   correlation_id AS "correlationId", last_failure_code AS "failureCode",
                   attempt_count AS "attemptCount", retry_count AS "retryCount",
                   next_attempt_at AS "nextAttemptAt", last_failure_summary AS "failureSummary",
                   failed_at AS "failedAt", version
            FROM t_incident_alert WHERE source_id = #{sourceId} AND external_alert_id = #{externalAlertId}
            """)
    AlertRow findByIdentity(@Param("sourceId") String sourceId, @Param("externalAlertId") String externalAlertId);

    @Select("""
            SELECT id, source_id AS "sourceId", external_alert_id AS "externalAlertId",
                   payload_digest AS "payloadDigest", processing_status AS "processingStatus",
                   aggregation_key AS "aggregationKey", fingerprint,
                   received_at AS "receivedAt",
                   processing_epoch AS "processingEpoch", incident_id AS "incidentId",
                   correlation_id AS "correlationId", last_failure_code AS "failureCode",
                   attempt_count AS "attemptCount", retry_count AS "retryCount",
                   next_attempt_at AS "nextAttemptAt", last_failure_summary AS "failureSummary",
                   failed_at AS "failedAt", version
            FROM t_incident_alert WHERE id = #{id}
            """)
    AlertRow findById(String id);

    @Select("""
            SELECT id, source_id AS "sourceId", external_alert_id AS "externalAlertId",
                   payload_digest AS "payloadDigest", processing_status AS "processingStatus",
                   aggregation_key AS "aggregationKey", fingerprint, received_at AS "receivedAt",
                   processing_epoch AS "processingEpoch", attempt_count AS "attemptCount",
                   retry_count AS "retryCount", last_failure_code AS "failureCode",
                   incident_id AS "incidentId", correlation_id AS "correlationId", version
            FROM t_incident_alert WHERE id = #{id} FOR UPDATE
            """)
    AlertRow lockById(String id);

    @Select("""
            SELECT id, source_id AS "sourceId", external_alert_id AS "externalAlertId",
                   payload_digest AS "payloadDigest", processing_status AS "processingStatus",
                   aggregation_key AS "aggregationKey", fingerprint, received_at AS "receivedAt",
                   processing_epoch AS "processingEpoch", incident_id AS "incidentId",
                   attempt_count AS "attemptCount", retry_count AS "retryCount",
                   correlation_id AS "correlationId", version
            FROM t_incident_alert
            WHERE id = #{id} AND processing_status = 'RECEIVED' AND next_attempt_at <= clock_timestamp()
            FOR UPDATE SKIP LOCKED
            """)
    AlertRow lockDueById(String id);

    @Select("""
            SELECT id, source_id AS "sourceId", processing_status AS "processingStatus",
                   aggregation_key AS "aggregationKey", fingerprint, received_at AS "receivedAt",
                   incident_id AS "incidentId", correlation_id AS "correlationId", version
            FROM t_incident_alert WHERE aggregation_key = #{aggregationKey}
            ORDER BY received_at, id
            """)
    List<AlertRow> findAllByAggregationKey(String aggregationKey);

    @Select("""
            SELECT id, source_id AS "sourceId", rule_id AS "ruleId", title, severity,
                   environment, service, resource_type AS "resourceType", resource_id AS "resourceId",
                   occurred_at AS "occurredAt",
                   received_at AS "receivedAt", correlation_id AS "correlationId"
            FROM t_incident_alert WHERE incident_id=#{incidentId} AND processing_status='PROCESSED'
            ORDER BY received_at,id LIMIT #{limit}
            """)
    List<AlertRow> findDiagnosisMembers(@Param("incidentId") String incidentId, @Param("limit") int limit);

    @Select("""
            SELECT id FROM t_incident_alert
            WHERE processing_status = 'RECEIVED' AND next_attempt_at <= clock_timestamp()
            ORDER BY next_attempt_at, received_at, id LIMIT #{limit}
            """)
    List<String> findDueIds(int limit);

    @Update("""
            UPDATE t_incident_alert SET incident_id = #{incidentId}, attachment_event_id = #{eventId},
                attached_at = clock_timestamp(), processing_status = 'PROCESSED', next_attempt_at = NULL,
                attempt_count = attempt_count + 1, version = version + 1, updated_at = clock_timestamp()
            WHERE id = #{alertId} AND processing_status = 'RECEIVED' AND incident_id IS NULL
            """)
    int attachOnce(@Param("alertId") String alertId, @Param("incidentId") String incidentId,
            @Param("eventId") String eventId);

    @Update("""
            UPDATE t_incident_alert SET attempt_count = attempt_count + 1, retry_count = retry_count + 1,
                next_attempt_at = #{nextAttemptAt}, last_failure_code = 'PROCESSING_FAILED',
                last_failure_summary = 'Processing failed; retry scheduled', version = version + 1,
                updated_at = clock_timestamp()
            WHERE id = #{id} AND processing_status = 'RECEIVED' AND version = #{expectedVersion}
            """)
    int scheduleRetry(@Param("id") String id, @Param("expectedVersion") long expectedVersion,
            @Param("nextAttemptAt") OffsetDateTime nextAttemptAt);

    @Update("""
            UPDATE t_incident_alert SET processing_status = 'FAILED', next_attempt_at = NULL,
                attempt_count = attempt_count + 1, last_failure_code = 'PROCESSING_FAILED',
                last_failure_summary = 'Processing failed after retries', failed_at = clock_timestamp(),
                version = version + 1, updated_at = clock_timestamp()
            WHERE id = #{id} AND processing_status = 'RECEIVED' AND version = #{expectedVersion}
            """)
    int failTerminal(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE t_incident_alert SET processing_status = 'RECEIVED', processing_epoch = processing_epoch + 1,
                attempt_count = 0, retry_count = 0, next_attempt_at = clock_timestamp(),
                last_failure_code = NULL, last_failure_summary = NULL, failed_at = NULL,
                version = version + 1, updated_at = clock_timestamp()
            WHERE id = #{id} AND processing_status = 'FAILED' AND version = #{expectedVersion}
            """)
    int replayFailed(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

    @Data
    class NewAlert {
        private String id;
        private String sourceId;
        private String externalAlertId;
        private String payloadJson;
        private String payloadDigest;
        private OffsetDateTime occurredAt;
        private String ruleId;
        private String title;
        private String severity;
        private String environment;
        private String service;
        private String resourceType;
        private String resourceId;
        private String summary;
        private String labelsJson;
        private String fingerprint;
        private String aggregationKey;
        private OffsetDateTime receivedAt;
        private String correlationId;
    }

    @Data
    class AlertRow {
        private String id;
        private String ruleId;
        private String title;
        private String severity;
        private String environment;
        private String service;
        private String resourceType;
        private String resourceId;
        private OffsetDateTime occurredAt;
        private String sourceId;
        private String externalAlertId;
        private String payloadDigest;
        private String processingStatus;
        private int processingEpoch;
        private String incidentId;
        private String correlationId;
        private String aggregationKey;
        private String fingerprint;
        private OffsetDateTime receivedAt;
        private String failureCode;
        private int attemptCount;
        private int retryCount;
        private OffsetDateTime nextAttemptAt;
        private String failureSummary;
        private OffsetDateTime failedAt;
        private long version;
    }
}
