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
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/** Recompute the reviewed output, including every fact, byte range, hash and permitted rule. */
@Component public final class DiagnosisEvidenceValidator {
    private final ExtractiveDiagnosisGenerator generator;
    public DiagnosisEvidenceValidator(ExtractiveDiagnosisGenerator generator){this.generator=generator;}
    public boolean valid(JsonNode snapshot,JsonNode output,OffsetDateTime now) {
        if(snapshot==null || output==null)return false;
        try{return canonical(generator.generate(snapshot,now)).equals(canonical(output));}
        catch(RuntimeException invalid){return false;}
    }
    public JsonNode validate(JsonNode snapshot,JsonNode output,OffsetDateTime now) {
        try (var observation = com.nageoffer.ai.ragent.framework.observability.ObservationScope.start("incident.diagnosis.validate", io.opentelemetry.api.trace.SpanKind.INTERNAL)) {
        return valid(snapshot,output,now)?output:failureResult("INVALID_EVIDENCE","INVALID_EVIDENCE");

        }
    }
}
