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

package com.nageoffer.ai.ragent.incident.action.dao;

import java.time.OffsetDateTime;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ExecutionMapper {
    String COLUMNS = """
            id, action_id AS "actionId", execution_key AS "executionKey",
            dispatch_request_id AS "dispatchRequestId", tool_name AS "toolName",
            tool_version AS "toolVersion", target_type AS "targetType", target_id AS "targetId",
            binding_digest AS "bindingDigest", max_execution_duration_ms AS "maxExecutionDurationMs",
            receipt_grace_period_ms AS "receiptGracePeriodMs",
            execution_started_at AS "executionStartedAt", receipt_due_at AS "receiptDueAt",
            current_receipt_id AS "currentReceiptId"
            """;

    @Insert("""
            INSERT INTO t_incident_action_execution (id, action_id, execution_key, dispatch_request_id,
                tool_name, tool_version, target_type, target_id, binding_digest,
                max_execution_duration_ms, receipt_grace_period_ms, created_at)
            VALUES (#{id}, #{actionId}, #{executionKey}, #{dispatchRequestId}, #{toolName},
                #{toolVersion}, #{targetType}, #{targetId}, #{bindingDigest},
                #{maxExecutionDurationMs}, #{receiptGracePeriodMs}, clock_timestamp())
            """)
    int insert(NewExecution execution);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action_execution WHERE action_id = #{actionId}")
    ExecutionRow findByActionId(String actionId);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action_execution WHERE id = #{id}")
    ExecutionRow findById(String id);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action_execution WHERE id = #{id} FOR UPDATE")
    ExecutionRow lockById(String id);

    @Update("""
            UPDATE t_incident_action_execution SET execution_started_at = #{startedAt},
                receipt_due_at = #{receiptDueAt}
            WHERE id = #{id} AND execution_started_at IS NULL AND receipt_due_at IS NULL
            """)
    int markStarted(@Param("id") String id, @Param("startedAt") OffsetDateTime startedAt,
            @Param("receiptDueAt") OffsetDateTime receiptDueAt);

    @Update("""
            UPDATE t_incident_action_execution SET current_receipt_id = #{receiptId}
            WHERE id = #{id} AND (current_receipt_id IS NULL OR current_receipt_id = #{expectedReceiptId})
            """)
    int setCurrentReceipt(@Param("id") String id, @Param("expectedReceiptId") String expectedReceiptId,
            @Param("receiptId") String receiptId);

    @Data
    class NewExecution {
        private String id;
        private String actionId;
        private String executionKey;
        private String dispatchRequestId;
        private String toolName;
        private String toolVersion;
        private String targetType;
        private String targetId;
        private String bindingDigest;
        private long maxExecutionDurationMs;
        private long receiptGracePeriodMs;
    }

    @Data
    class ExecutionRow extends NewExecution {
        private OffsetDateTime executionStartedAt;
        private OffsetDateTime receiptDueAt;
        private String currentReceiptId;
    }
}
