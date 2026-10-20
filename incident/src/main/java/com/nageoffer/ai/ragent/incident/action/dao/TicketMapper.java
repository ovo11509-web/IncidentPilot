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

public interface TicketMapper {
    String COLUMNS = """
            id, execution_id AS "executionId", secret_digest AS "secretDigest", status,
            issued_at AS "issuedAt", expires_at AS "expiresAt", consumed_at AS "consumedAt",
            invalidation_reason AS "invalidationReason", replaced_by_ticket_id AS "replacedByTicketId"
            """;

    @Insert("""
            INSERT INTO t_incident_execution_ticket (id, execution_id, secret_digest, status,
                issued_at, expires_at) VALUES (#{id}, #{executionId}, #{secretDigest}, 'ISSUED',
                #{issuedAt}, #{expiresAt})
            """)
    int insertIssued(NewTicket ticket);

    @Select("SELECT " + COLUMNS + " FROM t_incident_execution_ticket WHERE secret_digest = #{digest} FOR UPDATE")
    TicketRow lockByDigest(String digest);

    @Select("SELECT " + COLUMNS + " FROM t_incident_execution_ticket WHERE execution_id = #{executionId} "
            + "ORDER BY issued_at, id")
    List<TicketRow> findByExecutionId(String executionId);

    @Select("SELECT " + COLUMNS + " FROM t_incident_execution_ticket WHERE execution_id = #{executionId} "
            + "AND status = 'ISSUED' FOR UPDATE")
    TicketRow lockIssued(String executionId);

    @Select("SELECT count(*) FROM t_incident_execution_ticket WHERE execution_id = #{executionId} "
            + "AND status = 'CONSUMED'")
    long countConsumed(String executionId);

    @Select("""
            SELECT EXISTS(SELECT 1 FROM t_incident_execution_ticket t
                JOIN t_incident_action_execution e ON e.id = t.execution_id
                JOIN t_incident_action a ON a.id = e.action_id
                WHERE e.id = #{executionId} AND t.secret_digest = #{digest}
                    AND t.status = 'ISSUED' AND t.expires_at > clock_timestamp()
                    AND a.state = 'DISPATCHING' AND a.authorization_due_at > clock_timestamp()
                    AND NOT EXISTS (SELECT 1 FROM t_incident_execution_ticket consumed
                        WHERE consumed.execution_id = e.id AND consumed.status = 'CONSUMED'))
            """)
    boolean canRetryBeforeConsumption(@Param("executionId") String executionId,
            @Param("digest") String digest);

    @Update("""
            UPDATE t_incident_execution_ticket SET status = 'CONSUMED', consumed_at = #{consumedAt}
            WHERE id = #{id} AND status = 'ISSUED' AND expires_at > #{consumedAt}
            """)
    int consume(@Param("id") String id, @Param("consumedAt") OffsetDateTime consumedAt);

    @Update("""
            UPDATE t_incident_execution_ticket SET status = 'EXPIRED', invalidation_reason = #{reason},
                replaced_by_ticket_id = #{replacementId}
            WHERE id = #{id} AND status = 'ISSUED'
            """)
    int expire(@Param("id") String id, @Param("reason") String reason,
            @Param("replacementId") String replacementId);

    @Update("UPDATE t_incident_execution_ticket SET replaced_by_ticket_id=#{replacementId} "
            + "WHERE id=#{id} AND status='EXPIRED' AND invalidation_reason='RECOVERY_REPLACED' "
            + "AND replaced_by_ticket_id IS NULL")
    int linkReplacement(@Param("id") String id, @Param("replacementId") String replacementId);

    @Data
    class NewTicket {
        private String id;
        private String executionId;
        private String secretDigest;
        private OffsetDateTime issuedAt;
        private OffsetDateTime expiresAt;
    }

    @Data
    class TicketRow extends NewTicket {
        private String status;
        private OffsetDateTime consumedAt;
        private String invalidationReason;
        private String replacedByTicketId;
    }
}
