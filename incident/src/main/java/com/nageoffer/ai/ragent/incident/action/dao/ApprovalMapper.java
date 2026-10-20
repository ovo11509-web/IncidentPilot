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

public interface ApprovalMapper {
    String COLUMNS = """
            id, action_id AS "actionId", status, version, binding_digest AS "bindingDigest",
            approval_due_at AS "approvalDueAt", decider_id AS "deciderId",
            role_snapshot AS "roleSnapshot", decided_at AS "decidedAt",
            authorization_due_at AS "authorizationDueAt", reason_code AS "reasonCode",
            reason_summary AS "reasonSummary"
            """;

    @Insert("""
            INSERT INTO t_incident_action_approval (id, action_id, status, binding_digest,
                approval_due_at, created_at) VALUES (#{id}, #{actionId}, 'PENDING',
                #{bindingDigest}, #{approvalDueAt}, clock_timestamp())
            """)
    int insertPending(@Param("id") String id, @Param("actionId") String actionId,
            @Param("bindingDigest") String bindingDigest, @Param("approvalDueAt") OffsetDateTime approvalDueAt);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action_approval WHERE action_id = #{actionId}")
    ApprovalRow findByActionId(String actionId);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action_approval WHERE action_id = #{actionId} FOR UPDATE")
    ApprovalRow lockByActionId(String actionId);

    @Update("""
            UPDATE t_incident_action_approval SET status = #{nextStatus}, version = version + 1,
                decider_id = #{deciderId}, role_snapshot = #{roleSnapshot}, decided_at = #{decidedAt},
                authorization_due_at = #{authorizationDueAt}, reason_code = #{reasonCode},
                reason_summary = #{reasonSummary}
            WHERE action_id = #{actionId} AND status = 'PENDING' AND version = #{expectedVersion}
              AND #{nextStatus} IN ('APPROVED','REJECTED','EXPIRED')
            """)
    int decide(@Param("actionId") String actionId, @Param("expectedVersion") long expectedVersion,
            @Param("nextStatus") String nextStatus, @Param("deciderId") String deciderId,
            @Param("roleSnapshot") String roleSnapshot, @Param("decidedAt") OffsetDateTime decidedAt,
            @Param("authorizationDueAt") OffsetDateTime authorizationDueAt,
            @Param("reasonCode") String reasonCode, @Param("reasonSummary") String reasonSummary);

    @Data
    class ApprovalRow {
        private String id;
        private String actionId;
        private String status;
        private long version;
        private String bindingDigest;
        private OffsetDateTime approvalDueAt;
        private String deciderId;
        private String roleSnapshot;
        private OffsetDateTime decidedAt;
        private OffsetDateTime authorizationDueAt;
        private String reasonCode;
        private String reasonSummary;
    }
}
