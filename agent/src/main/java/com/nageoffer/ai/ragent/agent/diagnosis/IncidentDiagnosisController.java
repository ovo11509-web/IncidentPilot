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
import com.nageoffer.ai.ragent.incident.diagnosis.web.DiagnosisResponses;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
@RestController @RequestMapping("/incident/v1") public final class IncidentDiagnosisController {
    private final IncidentDiagnosisOrchestrator orchestrator;
    public IncidentDiagnosisController(IncidentDiagnosisOrchestrator orchestrator){this.orchestrator=orchestrator;}
    @PostMapping("/incidents/{incidentId}/diagnoses") public Object diagnose(@PathVariable String incidentId,@RequestBody String raw){
        staff();var fields=Set.of("sourceRequestId","expectedIncidentVersion");var input=body(raw,fields,fields);
        var result=orchestrator.diagnose(incidentId,safeId(text(input,"sourceRequestId")),version(input,"expectedIncidentVersion"));
        return ResponseEntity.status(result.status()).body(DiagnosisResponses.success(result.body()));
    }
}
