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
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** Protected Incident aggregate writes; no unrestricted update or delete operation. */
public interface IncidentMapper {

    @Select("""
            <script>
            SELECT i.id, i.lifecycle_state AS "lifecycleState", i.version, i.alert_count AS "alertCount",
                i.episode_start_at AS "episodeStartAt", i.first_alert_received_at AS "firstAlertReceivedAt",
                i.last_alert_received_at AS "lastAlertReceivedAt", anchor.title AS "summaryTitle",
                anchor.service AS "summaryService", anchor.environment AS "summaryEnvironment",
                anchor.severity AS "summarySeverity"
            FROM t_incident i LEFT JOIN t_incident_alert anchor
                ON anchor.id=i.episode_anchor_alert_id AND anchor.incident_id=i.id
                AND anchor.processing_status='PROCESSED'
            WHERE 1=1
            <if test="state != null">AND i.lifecycle_state = #{state}</if>
            <if test="q != null or service != null or environment != null or severity != null">
                AND (
                    <if test="q != null and service == null and environment == null and severity == null">
                        strpos(i.id,#{q}) > 0 OR
                    </if>
                    EXISTS (SELECT 1 FROM t_incident_alert a
                        WHERE a.incident_id=i.id AND a.processing_status='PROCESSED'
                        <if test="service != null">AND lower(a.service)=lower(#{service})</if>
                        <if test="environment != null">AND lower(a.environment)=lower(#{environment})</if>
                        <if test="severity != null">AND a.severity=#{severity}</if>
                        <if test="q != null">AND (strpos(i.id,#{q}) > 0 OR strpos(lower(a.title),lower(#{q})) > 0)</if>
                    )
                )
            </if>
            <if test="afterId != null">AND (i.episode_start_at,i.id) &lt; (#{afterTime},#{afterId})</if>
            ORDER BY i.episode_start_at DESC,i.id DESC LIMIT #{limit}
            </script>
            """)
    List<IncidentRow> findOperationsPage(@Param("state") String state,
            @Param("afterTime") OffsetDateTime afterTime, @Param("afterId") String afterId,
            @Param("limit") int limit, @Param("q") String q, @Param("service") String service,
            @Param("environment") String environment, @Param("severity") String severity);

    @Insert("""
            INSERT INTO t_incident (
                id, aggregation_key, fingerprint, aggregation_rule_version, aggregation_window_seconds,
                episode_start_at, episode_anchor_alert_id, first_alert_received_at, last_alert_received_at,
                alert_count, lifecycle_state, lifecycle_sealed_at, aggregation_slot_active, born_sealed,
                created_event_id, timeline_seq, version, created_at, updated_at
            ) VALUES (
                #{id}, #{aggregationKey}, #{fingerprint}, #{aggregationRuleVersion}, #{aggregationWindowSeconds},
                #{episodeStartAt}, #{episodeAnchorAlertId}, #{firstAlertReceivedAt}, #{lastAlertReceivedAt},
                #{alertCount}, 'OPEN', NULL, #{aggregationSlotActive}, #{bornSealed},
                #{createdEventId}, 2, 1, clock_timestamp(), clock_timestamp()
            )
            """)
    int insert(NewIncident incident);

    @Select("""
            SELECT id, aggregation_key AS "aggregationKey", fingerprint,
                   aggregation_rule_version AS "aggregationRuleVersion",
                   aggregation_window_seconds AS "aggregationWindowSeconds",
                   episode_start_at AS "episodeStartAt", episode_anchor_alert_id AS "episodeAnchorAlertId",
                   first_alert_received_at AS "firstAlertReceivedAt",
                   last_alert_received_at AS "lastAlertReceivedAt", alert_count AS "alertCount",
                   born_sealed AS "bornSealed", created_event_id AS "createdEventId",
                   lifecycle_state AS "lifecycleState",
                   lifecycle_sealed_at AS "lifecycleSealedAt", aggregation_slot_active AS "aggregationSlotActive",
                   timeline_seq AS "timelineSeq", version
            FROM t_incident WHERE id = #{id}
            """)
    IncidentRow findById(String id);

    @Select("""
            SELECT id, aggregation_key AS "aggregationKey", fingerprint,
                   aggregation_rule_version AS "aggregationRuleVersion",
                   aggregation_window_seconds AS "aggregationWindowSeconds",
                   episode_start_at AS "episodeStartAt", episode_anchor_alert_id AS "episodeAnchorAlertId",
                   first_alert_received_at AS "firstAlertReceivedAt",
                   last_alert_received_at AS "lastAlertReceivedAt", alert_count AS "alertCount",
                   born_sealed AS "bornSealed", created_event_id AS "createdEventId",
                   lifecycle_state AS "lifecycleState", lifecycle_sealed_at AS "lifecycleSealedAt",
                   aggregation_slot_active AS "aggregationSlotActive", timeline_seq AS "timelineSeq", version
            FROM t_incident WHERE id = #{id} FOR UPDATE
            """)
    IncidentRow lockById(String id);

    @Select("""
            SELECT EXISTS (SELECT 1 FROM t_incident
                WHERE aggregation_key = #{aggregationKey} AND lifecycle_state <> 'CLOSED'
                  AND (episode_start_at, episode_anchor_alert_id) > (#{episodeStartAt}, #{anchorId}))
            """)
    boolean hasNewerUnclosed(@Param("aggregationKey") String aggregationKey,
            @Param("episodeStartAt") OffsetDateTime episodeStartAt, @Param("anchorId") String anchorId);

    @Select("""
            SELECT id, aggregation_key AS "aggregationKey", fingerprint,
                   aggregation_rule_version AS "aggregationRuleVersion",
                   aggregation_window_seconds AS "aggregationWindowSeconds",
                   episode_start_at AS "episodeStartAt", episode_anchor_alert_id AS "episodeAnchorAlertId",
                   first_alert_received_at AS "firstAlertReceivedAt",
                   last_alert_received_at AS "lastAlertReceivedAt", alert_count AS "alertCount",
                   born_sealed AS "bornSealed", created_event_id AS "createdEventId",
                   lifecycle_state AS "lifecycleState", lifecycle_sealed_at AS "lifecycleSealedAt",
                   aggregation_slot_active AS "aggregationSlotActive", timeline_seq AS "timelineSeq", version
            FROM t_incident WHERE aggregation_key = #{aggregationKey}
            ORDER BY episode_start_at, episode_anchor_alert_id
            """)
    List<IncidentRow> findAllByAggregationKey(String aggregationKey);

    @Select("""
            SELECT id, aggregation_key AS "aggregationKey", fingerprint,
                   aggregation_rule_version AS "aggregationRuleVersion",
                   aggregation_window_seconds AS "aggregationWindowSeconds",
                   episode_start_at AS "episodeStartAt", episode_anchor_alert_id AS "episodeAnchorAlertId",
                   first_alert_received_at AS "firstAlertReceivedAt",
                   last_alert_received_at AS "lastAlertReceivedAt", alert_count AS "alertCount",
                   born_sealed AS "bornSealed", created_event_id AS "createdEventId",
                   lifecycle_state AS "lifecycleState", lifecycle_sealed_at AS "lifecycleSealedAt",
                   aggregation_slot_active AS "aggregationSlotActive", timeline_seq AS "timelineSeq", version
            FROM t_incident WHERE aggregation_key = #{aggregationKey} AND episode_anchor_alert_id = #{anchorId}
            FOR UPDATE
            """)
    IncidentRow lockByEpisode(@Param("aggregationKey") String aggregationKey, @Param("anchorId") String anchorId);

    @Select("SELECT clock_timestamp()")
    OffsetDateTime databaseNow();

    @Select("""
            SELECT id, aggregation_key AS "aggregationKey", lifecycle_state AS "lifecycleState",
                   episode_start_at AS "episodeStartAt", episode_anchor_alert_id AS "episodeAnchorAlertId",
                   lifecycle_sealed_at AS "lifecycleSealedAt", aggregation_slot_active AS "aggregationSlotActive",
                   timeline_seq AS "timelineSeq", version
            FROM t_incident WHERE aggregation_key = #{aggregationKey} AND aggregation_slot_active = true
            FOR UPDATE
            """)
    IncidentRow lockActiveByKey(String aggregationKey);

    @Update("""
            UPDATE t_incident SET lifecycle_state = #{nextState},
                lifecycle_sealed_at = CASE WHEN lifecycle_sealed_at IS NULL AND #{nextState} = 'RESOLVED'
                    THEN #{sealedAt} ELSE lifecycle_sealed_at END,
                aggregation_slot_active = CASE WHEN #{nextState} = 'RESOLVED' THEN false
                    ELSE aggregation_slot_active END,
                timeline_seq = timeline_seq + #{timelineFacts}, version = version + 1,
                updated_at = clock_timestamp()
            WHERE id = #{id} AND lifecycle_state = #{expectedState} AND version = #{expectedVersion}
            """)
    int transition(@Param("id") String id, @Param("expectedState") String expectedState,
            @Param("expectedVersion") long expectedVersion, @Param("nextState") String nextState,
            @Param("sealedAt") OffsetDateTime sealedAt, @Param("timelineFacts") int timelineFacts);

    @Update("""
            UPDATE t_incident SET aggregation_slot_active = false, version = version + 1,
                updated_at = clock_timestamp()
            WHERE id = #{id} AND aggregation_slot_active = true AND version = #{expectedVersion}
            """)
    int closeExpiredSlot(@Param("id") String id, @Param("expectedVersion") long expectedVersion);

    @Update("""
            UPDATE t_incident SET first_alert_received_at = least(first_alert_received_at, #{receivedAt}),
                last_alert_received_at = greatest(last_alert_received_at, #{receivedAt}),
                alert_count = alert_count + 1, timeline_seq = timeline_seq + 1,
                version = version + 1, updated_at = clock_timestamp()
            WHERE id = #{id} AND version = #{expectedVersion}
            """)
    int addAttachedAlert(@Param("id") String id, @Param("expectedVersion") long expectedVersion,
            @Param("receivedAt") OffsetDateTime receivedAt);

    @Update("""
            UPDATE t_incident SET timeline_seq = timeline_seq + #{facts}, updated_at = clock_timestamp()
            WHERE id = #{id} AND timeline_seq = #{expectedSequence} AND version = #{expectedVersion}
            """)
    int reserveTimeline(@Param("id") String id, @Param("expectedSequence") long expectedSequence,
            @Param("expectedVersion") long expectedVersion, @Param("facts") int facts);

    @Data
    class NewIncident {
        private String id;
        private String aggregationKey;
        private String fingerprint;
        private String aggregationRuleVersion;
        private int aggregationWindowSeconds;
        private OffsetDateTime episodeStartAt;
        private String episodeAnchorAlertId;
        private OffsetDateTime firstAlertReceivedAt;
        private OffsetDateTime lastAlertReceivedAt;
        private int alertCount;
        private boolean aggregationSlotActive;
        private boolean bornSealed;
        private String createdEventId;
    }

    @Data
    class IncidentRow {
        private String id;
        private String aggregationKey;
        private String fingerprint;
        private String aggregationRuleVersion;
        private int aggregationWindowSeconds;
        private OffsetDateTime episodeStartAt;
        private String episodeAnchorAlertId;
        private OffsetDateTime firstAlertReceivedAt;
        private OffsetDateTime lastAlertReceivedAt;
        private int alertCount;
        private boolean bornSealed;
        private String createdEventId;
        private String lifecycleState;
        private OffsetDateTime lifecycleSealedAt;
        private boolean aggregationSlotActive;
        private long timelineSeq;
        private long version;
        private String summaryTitle;
        private String summaryService;
        private String summaryEnvironment;
        private String summarySeverity;
    }
}
