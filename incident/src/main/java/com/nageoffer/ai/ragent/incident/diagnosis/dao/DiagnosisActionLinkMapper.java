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
public interface DiagnosisActionLinkMapper {
String COLUMNS = "action_id AS \"actionId\", incident_id AS \"incidentId\", diagnosis_id AS \"diagnosisId\", recommendation_id AS \"recommendationId\", recommendation_digest AS \"recommendationDigest\", diagnosis_digest AS \"diagnosisDigest\", facts_digest AS \"factsDigest\", actor_id AS \"actorId\", source_request_id AS \"sourceRequestId\", selection_digest AS \"selectionDigest\", created_at AS \"createdAt\"";
@Insert("INSERT INTO t_incident_diagnosis_action_link (action_id, incident_id, diagnosis_id, recommendation_id, recommendation_digest, diagnosis_digest, facts_digest, actor_id, source_request_id, selection_digest, created_at) VALUES (#{actionId}, #{incidentId}, #{diagnosisId}, #{recommendationId}, #{recommendationDigest}, #{diagnosisDigest}, #{factsDigest}, #{actorId}, #{sourceRequestId}, #{selectionDigest}, #{createdAt})") int insert(Row row);

@Select("SELECT " + COLUMNS + " FROM t_incident_diagnosis_action_link WHERE action_id=#{id}") Row find(String id);
@Select("SELECT " + COLUMNS + " FROM t_incident_diagnosis_action_link WHERE incident_id=#{incidentId} AND actor_id=#{actorId} AND source_request_id=#{sourceRequestId}") Row bySource(@Param("incidentId") String incidentId,@Param("actorId") String actorId,@Param("sourceRequestId") String sourceRequestId);

@Data class Row {
private String actionId;
private String incidentId;
private String diagnosisId;
private String recommendationId;
private String recommendationDigest;
private String diagnosisDigest;
private String factsDigest;
private String actorId;
private String sourceRequestId;
private String selectionDigest;
private OffsetDateTime createdAt;
}
}
