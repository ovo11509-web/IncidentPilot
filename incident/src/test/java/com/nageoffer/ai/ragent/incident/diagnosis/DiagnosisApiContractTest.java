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

package com.nageoffer.ai.ragent.incident.diagnosis;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.diagnosis.web.*;
import com.nageoffer.ai.ragent.incident.diagnosis.service.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.Test;
import java.util.Map;
class DiagnosisApiContractTest {
    @Test void strictSelectionBodyAndReadonlyQueryRoutes() throws Exception {
        try(var auth=mockStatic(StpUtil.class)) {
            var selects=mock(DiagnosisActionSelectionService.class);var queries=mock(DiagnosisQueryService.class);
            when(queries.get("101")).thenReturn(Map.of("diagnosisId","101","stale",false,"snapshot",wire(parse("{\"alerts\":[{\"alertId\":\"11\"}]}"))));
            var mvc=MockMvcBuilders.standaloneSetup(new DiagnosisActionController(selects),new DiagnosisQueryController(queries)).setControllerAdvice(new DiagnosisErrorAdvice()).build();
            String body="{\"sourceRequestId\":\"select\",\"expectedIncidentVersion\":0,\"diagnosisDigest\":\"a\",\"recommendationDigest\":\"b\"}";
            for(String invalid:new String[]{body.replace("{","{\"decision\":\"APPROVE\","),body.replace("{","{\"toolName\":\"destroy\","),body.replace("{","{\"sourceRequestId\":\"duplicate\","),"null",body+" {}"})
                mvc.perform(post("/incident/v1/diagnoses/101/recommendations/R2/actions").contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
            mvc.perform(post("/incident/v1/diagnoses/101/recommendations/R2/actions").content("x".repeat(8193))).andExpect(status().isPayloadTooLarge());
            verifyNoInteractions(selects);mvc.perform(get("/incident/v1/diagnoses/101")).andExpect(status().isOk()).andExpect(jsonPath("$.data.snapshot.alerts[0].alertId").value("11"));verify(queries).get("101");
        }
    }
    @Test void canonicalOrderingDuplicateJsonAndVersionValidation(){assertEquals(digest(parse("{\"b\":2,\"a\":1}")),digest(parse("{\"a\":1,\"b\":2}")));assertThrows(Failure.class,()->parse("{\"a\":1,\"a\":2}"));assertThrows(Failure.class,()->version(parse("{\"v\":1.5}"),"v"));}
}
