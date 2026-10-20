/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.incident.action.dao;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

/** Append-only receipt history; conflict handling inserts new facts and never overwrites a receipt. */
public interface ReceiptMapper {
    String COLUMNS = """
            id, execution_id AS "executionId", action_id AS "actionId", request_id AS "requestId",
            receipt_version AS "receiptVersion", receipt_digest AS "receiptDigest",
            result_status AS "resultStatus", result_code AS "resultCode",
            tool_name AS "toolName", tool_version AS "toolVersion",
            target_type AS "targetType", target_id AS "targetId", execution_key AS "executionKey",
            occurred_at AS "occurredAt", accepted_at AS "acceptedAt",
            summary_json::text AS "summaryJson", disposition
            """;

    @Insert("""
            INSERT INTO t_incident_action_receipt (id, execution_id, action_id, request_id,
                receipt_version, receipt_digest, result_status, result_code, tool_name,
                tool_version, target_type, target_id, execution_key, occurred_at, accepted_at,
                summary_json, disposition)
            VALUES (#{id}, #{executionId}, #{actionId}, #{requestId}, 1, #{receiptDigest},
                #{resultStatus}, #{resultCode}, #{toolName}, #{toolVersion}, #{targetType},
                #{targetId}, #{executionKey}, #{occurredAt}, #{acceptedAt},
                CAST(#{summaryJson} AS jsonb), #{disposition})
            """)
    int insert(ReceiptRow receipt);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action_receipt WHERE id = #{id}")
    ReceiptRow findById(String id);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action_receipt WHERE execution_id = #{executionId} "
            + "ORDER BY accepted_at, id")
    List<ReceiptRow> findByExecutionId(String executionId);

    @Data
    class ReceiptRow {
        private String id;
        private String executionId;
        private String actionId;
        private String requestId;
        private int receiptVersion = 1;
        private String receiptDigest;
        private String resultStatus;
        private String resultCode;
        private String toolName;
        private String toolVersion;
        private String targetType;
        private String targetId;
        private String executionKey;
        private OffsetDateTime occurredAt;
        private OffsetDateTime acceptedAt;
        private String summaryJson;
        private String disposition;
    }
}
