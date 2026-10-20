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

package com.nageoffer.ai.ragent.incident.outbox.dao;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Transactional Outbox writes and short-lease relay access. */
public interface OutboxMapper {

    @Insert("""
            INSERT INTO t_incident_outbox (
                event_id, business_event_key, incident_id, aggregate_version, event_type, schema_version,
                correlation_id, occurred_at, payload_json, publish_status, attempt_count, next_attempt_at,
                created_at, updated_at
            ) VALUES (
                #{eventId}, #{businessEventKey}, #{incidentId}, #{aggregateVersion}, #{eventType}, 1,
                #{correlationId}, #{occurredAt}, CAST(#{payloadJson} AS jsonb), 'PENDING', 0,
                clock_timestamp(), clock_timestamp(), clock_timestamp()
            )
            """)
    int insert(NewEvent event);

    @Update("""
            WITH due AS (
                SELECT event_id FROM t_incident_outbox
                WHERE publish_status = 'PENDING' AND next_attempt_at <= clock_timestamp()
                  AND (claim_until IS NULL OR claim_until <= clock_timestamp())
                ORDER BY next_attempt_at, created_at, event_id
                FOR UPDATE SKIP LOCKED LIMIT #{limit}
            )
            UPDATE t_incident_outbox o SET claim_token = #{claimToken},
                claim_until = clock_timestamp() + make_interval(secs => #{leaseSeconds}),
                attempt_count = o.attempt_count + 1, updated_at = clock_timestamp()
            FROM due WHERE o.event_id = due.event_id
            """)
    int claimDue(@Param("claimToken") String claimToken, @Param("leaseSeconds") int leaseSeconds,
            @Param("limit") int limit);

    @Select("""
            SELECT event_id AS "eventId", business_event_key AS "businessEventKey",
                   incident_id AS "incidentId", aggregate_version AS "aggregateVersion",
                   event_type AS "eventType", correlation_id AS "correlationId",
                   occurred_at AS "occurredAt", payload_json::text AS "payloadJson",
                   publish_status AS "publishStatus", attempt_count AS "attemptCount",
                   claim_token AS "claimToken", claim_until AS "claimUntil"
            FROM t_incident_outbox WHERE claim_token = #{claimToken} ORDER BY created_at, event_id
            """)
    List<EventRow> findClaimed(String claimToken);

    @Select("""
            SELECT event_id AS "eventId", business_event_key AS "businessEventKey",
                   incident_id AS "incidentId", aggregate_version AS "aggregateVersion",
                   event_type AS "eventType", correlation_id AS "correlationId",
                   occurred_at AS "occurredAt", payload_json::text AS "payloadJson",
                   publish_status AS "publishStatus", attempt_count AS "attemptCount",
                   claim_token AS "claimToken", claim_until AS "claimUntil"
            FROM t_incident_outbox WHERE event_id = #{eventId}
            """)
    EventRow findById(String eventId);

    @Update("""
            UPDATE t_incident_outbox SET publish_status = 'PUBLISHED', next_attempt_at = NULL,
                claim_token = NULL, claim_until = NULL, published_at = clock_timestamp(),
                broker_message_id = #{brokerMessageId}, last_error_code = NULL, last_error_summary = NULL,
                updated_at = clock_timestamp()
            WHERE event_id = #{eventId} AND publish_status = 'PENDING' AND claim_token = #{claimToken}
            """)
    int markPublished(@Param("eventId") String eventId, @Param("claimToken") String claimToken,
            @Param("brokerMessageId") String brokerMessageId);

    @Update("""
            UPDATE t_incident_outbox SET claim_token = NULL, claim_until = NULL,
                next_attempt_at = #{nextAttemptAt}, last_error_code = #{errorCode},
                last_error_summary = #{errorSummary}, updated_at = clock_timestamp()
            WHERE event_id = #{eventId} AND publish_status = 'PENDING' AND claim_token = #{claimToken}
            """)
    int reschedule(@Param("eventId") String eventId, @Param("claimToken") String claimToken,
            @Param("nextAttemptAt") OffsetDateTime nextAttemptAt, @Param("errorCode") String errorCode,
            @Param("errorSummary") String errorSummary);

    @Data
    class NewEvent {
        private String eventId;
        private String businessEventKey;
        private String incidentId;
        private long aggregateVersion;
        private String eventType;
        private String correlationId;
        private OffsetDateTime occurredAt;
        private String payloadJson;
    }

    @Data
    class EventRow {
        private String eventId;
        private String businessEventKey;
        private String incidentId;
        private long aggregateVersion;
        private String eventType;
        private String correlationId;
        private OffsetDateTime occurredAt;
        private String payloadJson;
        private String publishStatus;
        private int attemptCount;
        private String claimToken;
        private OffsetDateTime claimUntil;
    }
}
