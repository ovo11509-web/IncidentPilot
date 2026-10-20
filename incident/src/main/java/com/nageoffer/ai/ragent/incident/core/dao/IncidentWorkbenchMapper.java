/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.nageoffer.ai.ragent.incident.core.dao;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Bounded read projections; no payload, actor or business-key material. */
public interface IncidentWorkbenchMapper {
    @Select("SELECT clock_timestamp()")
    OffsetDateTime observedAt();

    @Select("""
        SELECT
          (SELECT count(*) FROM t_incident WHERE lifecycle_state IN ('OPEN','ACKNOWLEDGED','MITIGATING')) AS "activeIncidents",
          (SELECT count(*) FROM t_incident_action WHERE state='PENDING_APPROVAL') AS "pendingApprovalActions",
          (SELECT count(*) FROM t_incident_action WHERE state='EXECUTING') AS "executingActions",
          (SELECT count(*) FROM t_incident_action WHERE state='UNKNOWN') AS "unknownActions"
        """)
    Counts counts();

    @Select("""
        WITH buckets AS (
          SELECT n, CAST(#{at} AS timestamptz) - interval '24 hours' + n * interval '1 hour' AS start_at,
                 CAST(#{at} AS timestamptz) - interval '24 hours' + (n+1) * interval '1 hour' AS end_at
          FROM generate_series(0,23) AS n
        )
        SELECT b.start_at AS "startAt", b.end_at AS "endAt", count(a.id) AS count
        FROM buckets b LEFT JOIN t_incident_alert a ON a.received_at >= b.start_at AND a.received_at < b.end_at
        GROUP BY b.n,b.start_at,b.end_at ORDER BY b.n
        """)
    List<Bucket> alertBuckets(@Param("at") OffsetDateTime at);

    @Select("""
        WITH actions AS (
          SELECT incident_id,
            count(*) FILTER (WHERE state='PENDING_APPROVAL') AS pending,
            count(*) FILTER (WHERE state='EXECUTING') AS executing,
            count(*) FILTER (WHERE state='UNKNOWN') AS unknown
          FROM t_incident_action WHERE state IN ('PENDING_APPROVAL','EXECUTING','UNKNOWN') GROUP BY incident_id
        )
        SELECT i.id AS "incidentId", i.lifecycle_state AS "lifecycleState", i.version, i.alert_count AS "alertCount",
          i.last_alert_received_at AS "lastAlertReceivedAt", i.updated_at AS "updatedAt",
          coalesce(a.pending,0) AS "pendingApprovalActionCount", coalesce(a.executing,0) AS "executingActionCount",
          coalesce(a.unknown,0) AS "unknownActionCount"
        FROM t_incident i LEFT JOIN actions a ON a.incident_id=i.id
        WHERE i.lifecycle_state IN ('OPEN','ACKNOWLEDGED','MITIGATING') OR a.pending > 0 OR a.unknown > 0
        ORDER BY (coalesce(a.unknown,0)>0) DESC, (coalesce(a.pending,0)>0) DESC,i.updated_at DESC,i.id DESC LIMIT 8
        """)
    List<Attention> attentionIncidents();

    @Select("""
        SELECT id AS "actionId",incident_id AS "incidentId",state,created_at AS "createdAt",approval_due_at AS "approvalDueAt"
        FROM t_incident_action WHERE state='PENDING_APPROVAL' ORDER BY approval_due_at,id LIMIT 8
        """)
    List<Pending> pendingActions();

    @Select("""
        SELECT id AS "eventId",incident_id AS "incidentId",sequence_no AS "sequenceNo",event_type AS "eventType",
          created_at AS "createdAt" FROM t_incident_timeline ORDER BY created_at DESC,id DESC LIMIT 8
        """)
    List<Activity> recentActivity();

    @Data class Counts { private long activeIncidents;private long pendingApprovalActions;private long executingActions;private long unknownActions; }
    @Data class Bucket { private OffsetDateTime startAt;private OffsetDateTime endAt;private long count; }
    @Data class Attention {
        private String incidentId;private String lifecycleState;private long version;private long alertCount;
        private OffsetDateTime lastAlertReceivedAt;private OffsetDateTime updatedAt;
        private long pendingApprovalActionCount;private long executingActionCount;private long unknownActionCount;
    }
    @Data class Pending {private String actionId;private String incidentId;private String state;private OffsetDateTime createdAt;private OffsetDateTime approvalDueAt;}
    @Data class Activity {private String eventId;private String incidentId;private long sequenceNo;private String eventType;private OffsetDateTime createdAt;}
}
