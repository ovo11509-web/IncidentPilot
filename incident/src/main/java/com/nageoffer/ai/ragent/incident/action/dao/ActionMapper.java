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
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Action writes are limited to state transitions; proposal bindings have no update method. */
public interface ActionMapper {
    String COLUMNS = """
            id, incident_id AS "incidentId", source_type AS "sourceType",
            source_request_id AS "sourceRequestId", proposed_by_type AS "proposedByType",
            proposed_by_id AS "proposedById", original_action_id AS "originalActionId",
            tool_name AS "toolName", tool_version AS "toolVersion", target_type AS "targetType",
            target_id AS "targetId", parameters_json::text AS "parametersJson",
            proposal_digest AS "proposalDigest", binding_version AS "bindingVersion",
            binding_digest AS "bindingDigest", execution_key AS "executionKey",
            state, version, approval_due_at AS "approvalDueAt",
            authorization_due_at AS "authorizationDueAt", last_reason_code AS "lastReasonCode"
            """;

    @Insert("""
            INSERT INTO t_incident_action (id, incident_id, source_type, source_request_id,
                proposed_by_type, proposed_by_id, original_action_id, tool_name, tool_version,
                target_type, target_id, parameters_json, proposal_digest, binding_version,
                binding_digest, execution_key, state, approval_due_at, created_at, updated_at)
            VALUES (#{id}, #{incidentId}, #{sourceType}, #{sourceRequestId}, #{proposedByType},
                #{proposedById}, #{originalActionId}, #{toolName}, #{toolVersion}, #{targetType},
                #{targetId}, CAST(#{parametersJson} AS jsonb), #{proposalDigest}, #{bindingVersion},
                #{bindingDigest}, #{executionKey}, 'PENDING_APPROVAL', #{approvalDueAt},
                clock_timestamp(), clock_timestamp())
            """)
    int insert(NewAction action);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE id = #{id}")
    ActionRow findById(String id);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE id = #{id} FOR UPDATE")
    ActionRow lockById(String id);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE incident_id = #{incidentId} "
            + "AND source_type = #{sourceType} AND source_request_id = #{sourceRequestId}")
    ActionRow findBySource(@Param("incidentId") String incidentId, @Param("sourceType") String sourceType,
            @Param("sourceRequestId") String sourceRequestId);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE incident_id = #{incidentId} "
            + "ORDER BY created_at, id")
    List<ActionRow> findByIncidentId(String incidentId);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE incident_id = #{incidentId} "
            + "AND state IN ('PENDING_APPROVAL','APPROVED','DISPATCHING') ORDER BY created_at, id FOR UPDATE")
    List<ActionRow> lockUnstartedByIncident(String incidentId);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE state = 'APPROVED' "
            + "AND authorization_due_at > clock_timestamp() ORDER BY authorization_due_at, id "
            + "FOR UPDATE SKIP LOCKED LIMIT #{limit}")
    List<ActionRow> lockApproved(int limit);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE "
            + "(state = 'PENDING_APPROVAL' AND approval_due_at <= clock_timestamp()) OR "
            + "(state = 'APPROVED' AND authorization_due_at <= clock_timestamp()) "
            + "ORDER BY id LIMIT #{limit}")
    List<ActionRow> findDue(int limit);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE state='APPROVED' "
            + "AND authorization_due_at > clock_timestamp() ORDER BY authorization_due_at,id LIMIT #{limit}")
    List<ActionRow> findDispatchCandidates(int limit);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action a WHERE a.state='DISPATCHING' "
            + "AND (a.authorization_due_at <= clock_timestamp() OR EXISTS ("
            + "SELECT 1 FROM t_incident_action_execution e WHERE e.action_id=a.id "
            + "AND e.execution_started_at IS NULL AND NOT EXISTS ("
            + "SELECT 1 FROM t_incident_execution_ticket t WHERE t.execution_id=e.id "
            + "AND t.status='CONSUMED') AND NOT EXISTS ("
            + "SELECT 1 FROM t_incident_execution_ticket t WHERE t.execution_id=e.id "
            + "AND t.status='ISSUED' AND t.expires_at > clock_timestamp()))) "
            + "ORDER BY a.id LIMIT #{limit}")
    List<ActionRow> findRecoveryCandidates(int limit);

    @Select("SELECT " + COLUMNS + " FROM t_incident_action WHERE state='EXECUTING' "
            + "AND EXISTS (SELECT 1 FROM t_incident_action_execution e "
            + "WHERE e.action_id=t_incident_action.id AND e.receipt_due_at <= clock_timestamp()) "
            + "ORDER BY id LIMIT #{limit}")
    List<ActionRow> findReceiptDeadlineCandidates(int limit);

    @Update("""
            UPDATE t_incident_action SET state = #{nextState}, version = version + 1,
                authorization_due_at = COALESCE(#{authorizationDueAt}, authorization_due_at),
                last_reason_code = #{reasonCode}, updated_at = clock_timestamp()
            WHERE id = #{id} AND state = #{expectedState} AND version = #{expectedVersion}
              AND (#{expectedState}, #{nextState}) IN (
                ('PENDING_APPROVAL','APPROVED'), ('PENDING_APPROVAL','APPROVAL_REJECTED'),
                ('PENDING_APPROVAL','EXPIRED'), ('APPROVED','DISPATCHING'),
                ('APPROVED','EXPIRED'), ('DISPATCHING','EXECUTING'),
                ('DISPATCHING','FAILED'), ('DISPATCHING','EXECUTION_REJECTED'),
                ('DISPATCHING','EXPIRED'), ('EXECUTING','SUCCEEDED'),
                ('EXECUTING','FAILED'), ('EXECUTING','EXECUTION_REJECTED'),
                ('EXECUTING','UNKNOWN'), ('UNKNOWN','SUCCEEDED'), ('UNKNOWN','FAILED'))
            """)
    int transition(@Param("id") String id, @Param("expectedState") String expectedState,
            @Param("expectedVersion") long expectedVersion, @Param("nextState") String nextState,
            @Param("authorizationDueAt") OffsetDateTime authorizationDueAt,
            @Param("reasonCode") String reasonCode);

    @Data
    class NewAction {
        private String id;
        private String incidentId;
        private String sourceType;
        private String sourceRequestId;
        private String proposedByType;
        private String proposedById;
        private String originalActionId;
        private String toolName;
        private String toolVersion;
        private String targetType;
        private String targetId;
        private String parametersJson;
        private String proposalDigest;
        private String bindingVersion;
        private String bindingDigest;
        private String executionKey;
        private OffsetDateTime approvalDueAt;
    }

    @Data
    class ActionRow extends NewAction {
        private String state;
        private long version;
        private OffsetDateTime authorizationDueAt;
        private String lastReasonCode;
    }
}
