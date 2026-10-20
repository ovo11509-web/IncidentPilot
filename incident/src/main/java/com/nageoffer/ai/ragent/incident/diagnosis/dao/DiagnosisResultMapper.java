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
public interface DiagnosisResultMapper {
String COLUMNS = "diagnosis_id AS \"diagnosisId\", status AS \"status\", reason_code AS \"reasonCode\", result_json AS \"resultJson\", result_digest AS \"resultDigest\", stale_at_publish AS \"staleAtPublish\", recorded_at AS \"recordedAt\"";
@Insert("INSERT INTO t_incident_diagnosis_result (diagnosis_id, status, reason_code, result_json, result_digest, stale_at_publish, recorded_at) VALUES (#{diagnosisId}, #{status}, #{reasonCode}, CAST(#{resultJson} AS jsonb), #{resultDigest}, #{staleAtPublish}, #{recordedAt})") int insert(Row row);
@Select("SELECT " + COLUMNS + " FROM t_incident_diagnosis_result WHERE diagnosis_id=#{id}") Row find(String id);
@Select("<script>SELECT " + COLUMNS + " FROM t_incident_diagnosis_result WHERE diagnosis_id IN "
    + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
List<Row> findPageResults(@Param("ids") List<String> ids);
@Data class Row {
private String diagnosisId;
private String status;
private String reasonCode;
private String resultJson;
private String resultDigest;
private boolean staleAtPublish;
private OffsetDateTime recordedAt;
}
}
