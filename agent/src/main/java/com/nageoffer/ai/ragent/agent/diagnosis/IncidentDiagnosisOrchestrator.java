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
import com.nageoffer.ai.ragent.incident.diagnosis.service.*;
import com.nageoffer.ai.ragent.rag.runbook.RunbookCorpus;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
@Service public final class IncidentDiagnosisOrchestrator {
    private final DiagnosisRecordService records;private final DiagnosisQueryService queries;
    private final RunbookCorpus corpus;private final ExtractiveDiagnosisGenerator generator;private final DiagnosisEvidenceValidator validator;
    public IncidentDiagnosisOrchestrator(DiagnosisRecordService records,DiagnosisQueryService queries,RunbookCorpus corpus,
            ExtractiveDiagnosisGenerator generator,DiagnosisEvidenceValidator validator){this.records=records;this.queries=queries;this.corpus=corpus;this.generator=generator;this.validator=validator;}
    public Response diagnose(String incidentId,String request,long version) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.diagnosis", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        var begin=records.begin(incidentId,request,version,corpus.version(),corpus.digest());
        observation.id("incidentId", incidentId).id("diagnosisId", begin.diagnosisId()).id("sourceRequestId", request)
                .outcome(begin.repeated() ? "REUSED" : "GENERATED");
        if(!begin.repeated()) {
            com.fasterxml.jackson.databind.JsonNode output;
            try {var now=OffsetDateTime.parse(begin.snapshot().path("capturedAt").asText());
                com.fasterxml.jackson.databind.JsonNode generated;
                try (var stage=com.nageoffer.ai.ragent.incident.observability.IncidentObservation.start("incident.diagnosis.generate")) {
                    generated=generator.generate(begin.snapshot(),now); stage.outcome("GENERATED");
                }
                output=validator.validate(begin.snapshot(),generated,now);
                observation.outcome(output.path("status").asText());
            }
            catch(RuntimeException unavailable){output=failureResult("FAILED","DIAGNOSIS_UNAVAILABLE");}
            records.complete(begin,output);
        }
        var view=new java.util.LinkedHashMap<>(queries.get(begin.diagnosisId()));view.remove("snapshot");view.remove("result");
        return new Response(begin.repeated()?("RUNNING".equals(view.get("requestState"))?202:200):201,view);

        }
    }
    public record Response(int status,Map<String,Object> body){}
}
