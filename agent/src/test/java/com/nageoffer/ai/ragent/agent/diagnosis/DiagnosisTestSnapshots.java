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

package com.nageoffer.ai.ragent.agent.diagnosis;
import static com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
final class DiagnosisTestSnapshots {
    static ObjectNode snapshot(String query,JsonNode scope){var s=JSON.createObjectNode().put("snapshotVersion",1).put("incidentId","100").put("incidentVersion",0).put("truncated",false);
        s.set("scope",scope.deepCopy());s.putArray("facts").addObject().put("factId","alert:11:title").put("sourceField","title").put("origin","ALERT_SOURCE_REPORT").put("value",query);
        s.put("factsDigest",digest(s));s.put("snapshotDigest",digest(s));return s;}
    static ObjectNode health(){return snapshot("健康检查失败 探针",JSON.createObjectNode().put("service","incident-demo").put("environment","demo").put("resourceType","sim_service"));}
}
