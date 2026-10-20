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

import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/** Append-only Alert facts; this mapper intentionally has no update or delete operation. */
public interface AlertAuditMapper {

    @Insert("""
            INSERT INTO t_incident_alert_audit (
                id, alert_id, fact_key, fact_type, occurred_at, actor_type, actor_id,
                request_id, correlation_id, processing_epoch, reason_code, reason_summary,
                failure_code, attempt_count_snapshot, metadata_json, created_at
            ) VALUES (
                #{id}, #{alertId}, #{factKey}, #{factType}, clock_timestamp(), #{actorType}, #{actorId},
                #{requestId}, #{correlationId}, #{processingEpoch}, #{reasonCode}, #{reasonSummary},
                #{failureCode}, #{attemptCountSnapshot}, CAST(#{metadataJson} AS jsonb), clock_timestamp()
            )
            """)
    int insert(Fact fact);

    @Select("""
            SELECT id, alert_id AS "alertId", fact_key AS "factKey", fact_type AS "factType",
                   correlation_id AS "correlationId", processing_epoch AS "processingEpoch"
            FROM t_incident_alert_audit WHERE alert_id = #{alertId} ORDER BY occurred_at, id
            """)
    List<Fact> findByAlertId(String alertId);

    @Data
    class Fact {
        private String id;
        private String alertId;
        private String factKey;
        private String factType;
        private String actorType;
        private String actorId;
        private String requestId;
        private String correlationId;
        private int processingEpoch;
        private String reasonCode;
        private String reasonSummary;
        private String failureCode;
        private Integer attemptCountSnapshot;
        private String metadataJson = "{}";
    }
}
