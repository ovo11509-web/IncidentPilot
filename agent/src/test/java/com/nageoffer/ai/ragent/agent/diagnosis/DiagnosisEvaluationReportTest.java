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

import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class DiagnosisEvaluationReportTest {
    private static final String RUN_ID="123e4567-e89b-12d3-a456-426614174000";
    private static final String SOURCE_DIGEST="A".repeat(64);
    private Properties originalProperties;

    @BeforeEach
    void savePropertiesAndSetGateIdentity() {
        originalProperties=(Properties)System.getProperties().clone();
        System.setProperty("delivery.gates","true");
        System.setProperty("lab.runId",RUN_ID);
        System.setProperty("lab.scenarioId","DELIVERY-BACKEND");
        System.setProperty("lab.sourceDigest",SOURCE_DIGEST);
    }

    @AfterEach
    void restoreProperties() {
        System.setProperties(originalProperties);
    }

    @ParameterizedTest
    @ValueSource(strings={"DELIVERY-SMOKE","DELIVERY-BACKEND"})
    void gateIdentityDoesNotInventGitMetadata(String scenarioId) throws Exception {
        System.setProperty("lab.scenarioId",scenarioId);
        var source=DiagnosisEvaluationReport.sourceIdentity();
        assertEquals(6,source.size());
        assertEquals("DELIVERY_GATE",source.path("sourceKind").asText());
        assertEquals(RUN_ID,source.path("gateRunId").asText());
        assertEquals(scenarioId,source.path("scenarioId").asText());
        assertEquals(SOURCE_DIGEST,source.path("sourceDigest").asText());
        assertTrue(source.path("gitHead").isNull());
        assertTrue(source.path("gitDirty").isNull());
    }

    @ParameterizedTest
    @ValueSource(strings={"lab.runId","lab.scenarioId","lab.sourceDigest"})
    void missingGateIdentityFailsClosed(String property) {
        System.clearProperty(property);
        assertThrows(IllegalStateException.class,DiagnosisEvaluationReport::sourceIdentity);
    }

    @ParameterizedTest
    @MethodSource("invalidIdentities")
    void malformedGateIdentityFailsClosed(String property,String value) {
        System.setProperty(property,value);
        assertThrows(IllegalStateException.class,DiagnosisEvaluationReport::sourceIdentity);
    }

    private static Stream<Arguments> invalidIdentities() {
        return Stream.of(
            Arguments.of("lab.runId","not-a-run-id"),
            Arguments.of("lab.runId","123E4567-E89B-12D3-A456-426614174000"),
            Arguments.of("lab.runId","1-1-1-1-1"),
            Arguments.of("lab.runId",RUN_ID+"\n"),
            Arguments.of("lab.scenarioId","delivery-backend"),
            Arguments.of("lab.scenarioId","DELIVERY-FRONTEND"),
            Arguments.of("lab.scenarioId","DELIVERY-BACKEND\n"),
            Arguments.of("lab.sourceDigest","a".repeat(64)),
            Arguments.of("lab.sourceDigest","A".repeat(63)),
            Arguments.of("lab.sourceDigest","G".repeat(64)),
            Arguments.of("lab.sourceDigest",SOURCE_DIGEST+"\n")
        );
    }
}
