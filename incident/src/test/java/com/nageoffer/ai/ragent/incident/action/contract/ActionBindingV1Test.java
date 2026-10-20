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

package com.nageoffer.ai.ragent.incident.action.contract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ActionBindingV1Test {
    @Test
    void proposalRetryDoesNotDependOnGeneratedActionOrExecutionKey() {
        var first = ActionBindingV1.proposalDigest("123", "USER", "request-1",
                ActionBindingV1.TOOL, "1", "SIM_SERVICE", "demo", "{\"desiredState\":\"PAUSED\"}");
        var retry = ActionBindingV1.proposalDigest("123", "USER", "request-1",
                ActionBindingV1.TOOL, "1", "SIM_SERVICE", "demo", "{ \"desiredState\" : \"PAUSED\" }");
        assertEquals(first, retry);
        assertNotEquals(first, ActionBindingV1.proposalDigest("123", "USER", "request-1",
                ActionBindingV1.TOOL, "1", "SIM_SERVICE", "demo", "{\"desiredState\":\"READY\"}"));
        assertNotEquals(ActionBindingV1.bindingDigest("action-1", "123", ActionBindingV1.TOOL,
                "1", "SIM_SERVICE", "demo", "{\"desiredState\":\"PAUSED\"}", "key-1"),
                ActionBindingV1.bindingDigest("action-1", "123", ActionBindingV1.TOOL,
                        "1", "SIM_SERVICE", "demo", "{\"desiredState\":\"PAUSED\"}", "key-2"));
    }

    @Test
    void frameSeparatesMissingNullEmptyAndFieldBoundaries() {
        assertNotEquals(java.util.Arrays.toString(ActionBindingV1.frame(ActionBindingV1.Field.missing())),
                java.util.Arrays.toString(ActionBindingV1.frame(ActionBindingV1.Field.of(null))));
        assertNotEquals(java.util.Arrays.toString(ActionBindingV1.frame(ActionBindingV1.Field.of(null))),
                java.util.Arrays.toString(ActionBindingV1.frame(ActionBindingV1.Field.of(""))));
        assertNotEquals(java.util.Arrays.toString(ActionBindingV1.frame(
                        ActionBindingV1.Field.of("ab"), ActionBindingV1.Field.of("c"))),
                java.util.Arrays.toString(ActionBindingV1.frame(
                        ActionBindingV1.Field.of("a"), ActionBindingV1.Field.of("bc"))));
        assertArrayEquals(ActionBindingV1.frame(ActionBindingV1.Field.of("é")),
                ActionBindingV1.frame(ActionBindingV1.Field.of("é")));
    }

    @Test
    void rejectsUnknownDuplicateAndInvalidParameters() {
        for (var json : new String[] {"{\"desiredState\":\"PAUSED\",\"extra\":1}",
                "{\"desiredState\":\"PAUSED\",\"desiredState\":\"READY\"}",
                "{\"desiredState\":null}", "{\"desiredState\":\"RUNNING\"}"}) {
            assertThrows(IllegalArgumentException.class, () -> ActionBindingV1.canonicalParameters(json));
        }
        assertThrows(IllegalArgumentException.class, () -> ActionBindingV1.bindingDigest(
                "action-1", "123", "old.write", "1", "SIM_SERVICE", "demo",
                "{\"desiredState\":\"PAUSED\"}", "key-1"));
    }
}
