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

package com.nageoffer.ai.ragent.incident.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nageoffer.ai.ragent.incident.core.service.IncidentStateMachine;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import org.junit.jupiter.api.Test;

class IncidentTransitionTest {
    private static final String KEY = "alert-incident-v1:" + "b".repeat(64);

    @Test
    void allAllowedEdgesPersistVersionsAndFirstResolutionAddsExactlyTwoFacts() throws Exception {
        try (var db = new IncidentDbFixture()) {
            db.insertAlert("10", KEY, db.incidents.databaseNow().minusSeconds(5));
            assertTrue(db.aggregation.processOne("10"));
            String id = db.incidents.findAllByAggregationKey(KEY).get(0).getId();
            var current = db.incidents.findById(id);
            for (String target : new String[] {"ACKNOWLEDGED", "MITIGATING", "ACKNOWLEDGED",
                    "RESOLVED", "ACKNOWLEDGED", "RESOLVED", "CLOSED"}) {
                current = db.transitions.transition(id, current.getLifecycleState(), current.getVersion(),
                        target, "safe reason", "user-42", "request-" + current.getVersion());
            }
            assertEquals("CLOSED", current.getLifecycleState());
            assertEquals(8, current.getVersion());
            assertNotNull(current.getLifecycleSealedAt());
            assertFalse(current.isAggregationSlotActive());
            assertEquals(10, db.timeline.findByIncidentId(id).size());
            assertEquals(1, db.timeline.findByIncidentId(id).stream()
                    .filter(fact -> "INCIDENT_AGGREGATION_SEALED".equals(fact.getEventType())).count());
            assertThrows(IncidentStateMachine.InvalidTransitionException.class,
                    () -> db.transitions.transition(id, "CLOSED", 8, "OPEN", "reason", "user-42", "closed"));
        }
    }

    @Test
    void staleVersionCannotOverwriteOrAddFacts() throws Exception {
        try (var db = new IncidentDbFixture()) {
            db.insertAlert("10", KEY, db.incidents.databaseNow().minusSeconds(5));
            db.aggregation.processOne("10");
            String id = db.incidents.findAllByAggregationKey(KEY).get(0).getId();
            db.transitions.transition(id, "OPEN", 1, "ACKNOWLEDGED", null, "user-1", "first");
            assertThrows(IncidentTransitionService.ConflictException.class,
                    () -> db.transitions.transition(id, "OPEN", 1, "RESOLVED", "reason", "user-2", "stale"));
            assertEquals("ACKNOWLEDGED", db.incidents.findById(id).getLifecycleState());
            assertEquals(3, db.timeline.findByIncidentId(id).size());
        }
    }

    @Test
    void newerUnclosedEpisodePreventsReopeningOlderResolvedIncident() throws Exception {
        try (var db = new IncidentDbFixture()) {
            db.insertAlert("10", KEY, db.incidents.databaseNow().minusSeconds(5));
            db.aggregation.processOne("10");
            String older = db.incidents.findAllByAggregationKey(KEY).get(0).getId();
            var resolved = db.transitions.transition(older, "OPEN", 1, "RESOLVED", "done", "user-1", "resolve");
            db.insertAlert("20", KEY, resolved.getLifecycleSealedAt().plusNanos(1000));
            assertTrue(db.aggregation.processOne("20"));
            assertEquals(2, db.count("t_incident"));
            assertThrows(IncidentTransitionService.ConflictException.class,
                    () -> db.transitions.transition(older, "RESOLVED", resolved.getVersion(),
                            "ACKNOWLEDGED", "reopen", "user-1", "reopen"));
            assertEquals("RESOLVED", db.incidents.findById(older).getLifecycleState());
            assertEquals(4, db.timeline.findByIncidentId(older).size());
        }
    }
}
