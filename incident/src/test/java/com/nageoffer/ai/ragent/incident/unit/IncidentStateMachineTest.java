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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nageoffer.ai.ragent.incident.core.service.IncidentStateMachine;
import org.junit.jupiter.api.Test;

class IncidentStateMachineTest {
    private final IncidentStateMachine machine = new IncidentStateMachine();

    @Test
    void allEightDocumentedEdgesAreAllowedWithAnAuthorizedPerson() {
        for (String[] edge : new String[][] {
                {"OPEN", "ACKNOWLEDGED"}, {"OPEN", "RESOLVED"},
                {"ACKNOWLEDGED", "MITIGATING"}, {"ACKNOWLEDGED", "RESOLVED"},
                {"MITIGATING", "ACKNOWLEDGED"}, {"MITIGATING", "RESOLVED"},
                {"RESOLVED", "ACKNOWLEDGED"}, {"RESOLVED", "CLOSED"}}) {
            assertDoesNotThrow(() -> machine.requireAllowed(edge[0], edge[1], "documented reason", "USER", "42"));
        }
    }

    @Test
    void illegalEdgesClosedStateAndMissingRequiredReasonAreRejected() {
        for (String[] edge : new String[][] {
                {"OPEN", "MITIGATING"}, {"ACKNOWLEDGED", "CLOSED"},
                {"MITIGATING", "OPEN"}, {"RESOLVED", "MITIGATING"},
                {"CLOSED", "OPEN"}, {"CLOSED", "RESOLVED"}, {"OPEN", "OPEN"}}) {
            assertThrows(IncidentStateMachine.InvalidTransitionException.class,
                    () -> machine.requireAllowed(edge[0], edge[1], "reason", "USER", "42"));
        }
        for (String[] edge : new String[][] {
                {"OPEN", "RESOLVED"}, {"ACKNOWLEDGED", "RESOLVED"},
                {"MITIGATING", "ACKNOWLEDGED"}, {"MITIGATING", "RESOLVED"},
                {"RESOLVED", "ACKNOWLEDGED"}}) {
            assertThrows(IncidentStateMachine.InvalidTransitionException.class,
                    () -> machine.requireAllowed(edge[0], edge[1], "  ", "USER", "42"));
        }
    }

    @Test
    void anonymousOrMachineActorCannotDriveMainState() {
        assertThrows(IncidentStateMachine.InvalidTransitionException.class,
                () -> machine.requireAllowed("OPEN", "ACKNOWLEDGED", null, "SOURCE", "source"));
        assertThrows(IncidentStateMachine.InvalidTransitionException.class,
                () -> machine.requireAllowed("OPEN", "ACKNOWLEDGED", null, "USER", ""));
    }
}
