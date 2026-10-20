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

package com.nageoffer.ai.ragent.incident.diagnosis.dao;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
public interface DiagnosisRequestMapper {
String COLUMNS = "id AS \"id\", incident_id AS \"incidentId\", actor_id AS \"actorId\", source_request_id AS \"sourceRequestId\", request_digest AS \"requestDigest\", snapshot_json AS \"snapshotJson\", snapshot_digest AS \"snapshotDigest\", facts_digest AS \"factsDigest\", incident_version AS \"incidentVersion\", timeline_watermark AS \"timelineWatermark\", corpus_version AS \"corpusVersion\", corpus_digest AS \"corpusDigest\", owner_token_digest AS \"ownerTokenDigest\", state AS \"state\", version AS \"version\", created_at AS \"createdAt\", deadline_at AS \"deadlineAt\", completed_at AS \"completedAt\"";
@Insert("INSERT INTO t_incident_diagnosis_request (id, incident_id, actor_id, source_request_id, request_digest, snapshot_json, snapshot_digest, facts_digest, incident_version, timeline_watermark, corpus_version, corpus_digest, owner_token_digest, state, version, created_at, deadline_at, completed_at) VALUES (#{id}, #{incidentId}, #{actorId}, #{sourceRequestId}, #{requestDigest}, CAST(#{snapshotJson} AS jsonb), #{snapshotDigest}, #{factsDigest}, #{incidentVersion}, #{timelineWatermark}, #{corpusVersion}, #{corpusDigest}, #{ownerTokenDigest}, #{state}, #{version}, #{createdAt}, #{deadlineAt}, #{completedAt})") int insert(Row row);

@Select("SELECT " + COLUMNS + " FROM t_incident_diagnosis_request WHERE id=#{id}") Row find(String id);
@Select("SELECT " + COLUMNS + " FROM t_incident_diagnosis_request WHERE id=#{id} FOR UPDATE") Row lock(String id);
@Select("SELECT " + COLUMNS + " FROM t_incident_diagnosis_request WHERE incident_id=#{incidentId} AND actor_id=#{actorId} AND source_request_id=#{sourceRequestId}") Row bySource(@Param("incidentId") String incidentId,@Param("actorId") String actorId,@Param("sourceRequestId") String sourceRequestId);
@Select("SELECT " + COLUMNS + " FROM t_incident_diagnosis_request WHERE incident_id=#{id} AND state='RUNNING' AND deadline_at<=clock_timestamp() ORDER BY created_at,id") List<Row> overdue(String id);
@Select("SELECT " + COLUMNS + " FROM t_incident_diagnosis_request WHERE incident_id=#{id} AND (CAST(#{afterId} AS varchar) IS NULL OR (created_at,id)>(SELECT created_at,id FROM t_incident_diagnosis_request WHERE id=#{afterId} AND incident_id=#{id})) ORDER BY created_at,id LIMIT #{limit}") List<Row> page(@Param("id") String id,@Param("afterId") String afterId,@Param("limit") int limit);
@Update("UPDATE t_incident_diagnosis_request SET state='COMPLETED',completed_at=clock_timestamp(),version=version+1 WHERE id=#{id} AND state='RUNNING' AND version=#{version}") int complete(@Param("id") String id,@Param("version") long version);

@Data class Row {
private String id;
private String incidentId;
private String actorId;
private String sourceRequestId;
private String requestDigest;
private String snapshotJson;
private String snapshotDigest;
private String factsDigest;
private long incidentVersion;
private long timelineWatermark;
private String corpusVersion;
private String corpusDigest;
private String ownerTokenDigest;
private String state;
private long version;
private OffsetDateTime createdAt;
private OffsetDateTime deadlineAt;
private OffsetDateTime completedAt;
}
}
