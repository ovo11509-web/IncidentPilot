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

package com.nageoffer.ai.ragent.rag.runbook;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class RunbookEvaluationReportTest {
    private static final String RUN_ID="12345678-1234-1234-1234-123456789abc";
    private final Map<String,String> saved=new LinkedHashMap<>();

    @BeforeEach void saveProperties() {
        for(String key:List.of("delivery.gates","lab.runId","lab.scenarioId","lab.sourceDigest"))
            saved.put(key,System.getProperty(key));
        System.setProperty("delivery.gates","true");
        System.setProperty("lab.runId",RUN_ID);
        System.setProperty("lab.scenarioId","DELIVERY-BACKEND");
        System.setProperty("lab.sourceDigest","A".repeat(64));
    }

    @AfterEach void restoreProperties() {
        saved.forEach((key,value)->{
            if(value==null)System.clearProperty(key);else System.setProperty(key,value);
        });
    }

    @ParameterizedTest @ValueSource(strings={"DELIVERY-SMOKE","DELIVERY-BACKEND"})
    void validGateSourceHasExplicitProvenanceWithoutClaimingGit(String scenario) throws Exception {
        System.setProperty("lab.scenarioId",scenario);
        var source=RunbookEvaluationReport.sourceIdentity();
        assertEquals("DELIVERY_GATE",source.path("sourceKind").asText());
        assertEquals(RUN_ID,source.path("gateRunId").asText());
        assertEquals(scenario,source.path("scenarioId").asText());
        assertEquals("A".repeat(64),source.path("sourceDigest").asText());
        assertTrue(source.has("gitHead") && source.path("gitHead").isNull());
        assertTrue(source.has("gitDirty") && source.path("gitDirty").isNull());
        assertFalse(source.has("runId"));
    }

    @ParameterizedTest @ValueSource(strings={"lab.runId","lab.scenarioId","lab.sourceDigest"})
    void missingGateIdentityFailsClosed(String key) {
        System.clearProperty(key);
        assertThrows(IllegalStateException.class,RunbookEvaluationReport::sourceIdentity);
    }

    @Test void invalidGateIdentityFailsClosed() {
        rejectValues("lab.runId",List.of("1-1-1-1-1",RUN_ID.toUpperCase(),"not-a-uuid"));
        rejectValues("lab.scenarioId",List.of("DELIVERY-FRONTEND","delivery-backend","DELIVERY-BACKEND\n"));
        rejectValues("lab.sourceDigest",List.of("a".repeat(64),"A".repeat(63),"A".repeat(65),""));
    }

    private void rejectValues(String key,List<String> invalid) {
        String original=System.getProperty(key);
        try {
            for(String value:invalid) {
                System.setProperty(key,value);
                assertThrows(IllegalStateException.class,RunbookEvaluationReport::sourceIdentity,key);
            }
        } finally {System.setProperty(key,original);}
    }
}
