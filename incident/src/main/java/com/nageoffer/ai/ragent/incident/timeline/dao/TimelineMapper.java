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

package com.nageoffer.ai.ragent.incident.timeline.dao;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/** Append-only Incident history; this mapper intentionally has no update or delete operation. */
public interface TimelineMapper {

    @Select("""
            <script>
            SELECT sequence_no AS "sequenceNo", business_event_key AS "businessEventKey",
                   event_type AS "eventType", related_entity_type AS "relatedEntityType",
                   related_entity_id AS "relatedEntityId"
            FROM t_incident_timeline
            WHERE incident_id = #{incidentId} AND related_entity_type IN ('ACTION','EXECUTION')
            <if test="actionId != null">
              AND ((related_entity_type='ACTION' AND related_entity_id=#{actionId})
                   OR (related_entity_type='EXECUTION' AND related_entity_id=#{executionId}))
            </if>
            ORDER BY sequence_no
            </script>
            """)
    List<Fact> findActionFacts(@Param("incidentId") String incidentId, @Param("actionId") String actionId,
            @Param("executionId") String executionId);

    @Insert("""
            INSERT INTO t_incident_timeline (
                id, incident_id, sequence_no, business_event_key, event_type, actor_type, actor_id,
                occurred_at, correlation_id, related_entity_type, related_entity_id, summary_json, created_at
            ) VALUES (
                #{id}, #{incidentId}, #{sequenceNo}, #{businessEventKey}, #{eventType}, #{actorType}, #{actorId},
                #{occurredAt}, #{correlationId}, #{relatedEntityType}, #{relatedEntityId},
                CAST(#{summaryJson} AS jsonb), clock_timestamp()
            )
            """)
    int insert(Fact fact);

    @Select("""
            SELECT id, incident_id AS "incidentId", sequence_no AS "sequenceNo",
                   business_event_key AS "businessEventKey", event_type AS "eventType",
                   actor_type AS "actorType", actor_id AS "actorId", occurred_at AS "occurredAt",
                   correlation_id AS "correlationId", related_entity_type AS "relatedEntityType",
                   related_entity_id AS "relatedEntityId", summary_json::text AS "summaryJson"
            FROM t_incident_timeline WHERE incident_id = #{incidentId} ORDER BY sequence_no
            """)
    List<Fact> findByIncidentId(String incidentId);

    @Select("SELECT id, incident_id AS \"incidentId\", sequence_no AS \"sequenceNo\", "
            + "business_event_key AS \"businessEventKey\", event_type AS \"eventType\", "
            + "actor_type AS \"actorType\", actor_id AS \"actorId\", occurred_at AS \"occurredAt\", "
            + "correlation_id AS \"correlationId\", related_entity_type AS \"relatedEntityType\", "
            + "related_entity_id AS \"relatedEntityId\", summary_json::text AS \"summaryJson\" "
            + "FROM t_incident_timeline WHERE business_event_key=#{key}")
    Fact findByBusinessKey(String key);

    @Select("""
            SELECT id, incident_id AS "incidentId", sequence_no AS "sequenceNo",
                   business_event_key AS "businessEventKey", event_type AS "eventType",
                   actor_type AS "actorType", actor_id AS "actorId", occurred_at AS "occurredAt",
                   correlation_id AS "correlationId", related_entity_type AS "relatedEntityType",
                   related_entity_id AS "relatedEntityId", summary_json::text AS "summaryJson"
            FROM t_incident_timeline
            WHERE incident_id = #{incidentId} AND sequence_no > #{afterSequence}
            ORDER BY sequence_no LIMIT #{limit}
            """)
    List<Fact> findPage(@Param("incidentId") String incidentId, @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);

    @Data
    class Fact {
        private String id;
        private String incidentId;
        private long sequenceNo;
        private String businessEventKey;
        private String eventType;
        private String actorType;
        private String actorId;
        private OffsetDateTime occurredAt;
        private String correlationId;
        private String relatedEntityType;
        private String relatedEntityId;
        private String summaryJson = "{}";
    }
}
