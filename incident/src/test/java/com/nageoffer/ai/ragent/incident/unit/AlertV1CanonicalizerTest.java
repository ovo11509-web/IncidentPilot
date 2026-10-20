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

package com.nageoffer.ai.ragent.incident.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AlertV1CanonicalizerTest {
    private static final String BASE = """
            {"externalAlertId":"e-1","eventType":"FIRING","occurredAt":"2026-09-23T00:00:00Z",
            "ruleId":"cpu","title":"CPU high","severity":"HIGH","environment":"prod",
            "service":"api","resource":{"type":"host","id":"h-1"}}
            """;
    private final AlertV1Canonicalizer canonicalizer = new AlertV1Canonicalizer(new IncidentProperties());

    @Test
    void whitespaceKeyOrderAndAbsentLabelsAreEquivalentButSummaryPresenceMatters() {
        var first = canonical(BASE);
        var reordered = canonical("""
                {"resource":{"id":"h-1","type":"host"},"service":"api","environment":"prod",
                "severity":"HIGH","title":"CPU high","ruleId":"cpu","occurredAt":"2026-09-23T00:00:00Z",
                "eventType":"FIRING","externalAlertId":"e-1","labels":{}}
                """);
        assertEquals(first.payloadDigest(), reordered.payloadDigest());
        assertEquals(first.payloadJson(), reordered.payloadJson());
        assertNotEquals(first.payloadDigest(), canonical(withField("\"summary\":\"text\"")).payloadDigest());
    }

    @Test
    void jsonStructureErrorsAreBadRequestCategory() {
        assertThrows(AlertV1Canonicalizer.InvalidJsonException.class,
                () -> canonicalizer.parseStructure(new byte[] {(byte) 0xc3, (byte) 0x28}));
        assertThrows(AlertV1Canonicalizer.InvalidJsonException.class,
                () -> canonicalizer.parseStructure("{\"a\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(AlertV1Canonicalizer.InvalidJsonException.class,
                () -> canonicalizer.parseStructure("{} {}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(AlertV1Canonicalizer.InvalidJsonException.class,
                () -> canonicalizer.parseStructure("{\"x\":null}".getBytes(StandardCharsets.UTF_8)));
        assertThrows(AlertV1Canonicalizer.InvalidJsonException.class,
                () -> canonicalizer.parseStructure("[]".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptedJsonWithUnknownFieldsInvalidLabelsAndEmptySummaryFailsContract() {
        assertThrows(AlertV1Canonicalizer.InvalidContractException.class,
                () -> canonical(withField("\"unknown\":true")));
        assertThrows(AlertV1Canonicalizer.InvalidContractException.class,
                () -> canonical(withField("\"summary\":\"  \"")));
        assertThrows(AlertV1Canonicalizer.InvalidContractException.class,
                () -> canonical(withField("\"labels\":{\"a\":1}")));
    }

    private AlertV1Canonicalizer.CanonicalAlert canonical(String json) {
        return canonicalizer.canonicalize(canonicalizer.parseStructure(json.getBytes(StandardCharsets.UTF_8)));
    }

    private String withField(String field) {
        String json = BASE.trim();
        return json.substring(0, json.length() - 1) + "," + field + "}";
    }
}
