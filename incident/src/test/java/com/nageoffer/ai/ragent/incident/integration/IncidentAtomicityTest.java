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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IncidentAtomicityTest {
    private static final String KEY = "alert-incident-v1:" + "b".repeat(64);

    @Test
    void associationTimelineAndOutboxFailuresEachRollbackTheWholeBusinessTransaction() throws Exception {
        try (var db = new IncidentDbFixture()) {
            db.insertAlert("10", KEY, db.incidents.databaseNow().minusSeconds(5));
            db.sql("""
                    CREATE FUNCTION reject_spec_a_write() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'injected write failure'; END $$
                    """);
            for (String table : new String[] {"t_incident_alert", "t_incident_timeline", "t_incident_outbox"}) {
                String action = table.equals("t_incident_alert") ? "UPDATE" : "INSERT";
                db.sql("CREATE TRIGGER reject_spec_a BEFORE " + action + " ON " + table
                        + " FOR EACH ROW EXECUTE FUNCTION reject_spec_a_write()");
                assertThrows(RuntimeException.class, () -> db.aggregation.processOne("10"));
                assertEquals("RECEIVED", db.alerts.findById("10").getProcessingStatus());
                assertEquals(0, db.count("t_incident"));
                assertEquals(0, db.count("t_incident_timeline"));
                assertEquals(0, db.count("t_incident_outbox"));
                db.sql("DROP TRIGGER reject_spec_a ON " + table);
            }
            assertTrue(db.aggregation.processOne("10"));
            String createdEventId = db.incidents.findAllByAggregationKey(KEY).get(0).getCreatedEventId();
            assertFalse(db.aggregation.processOne("10"));
            assertEquals(createdEventId, db.incidents.findAllByAggregationKey(KEY).get(0).getCreatedEventId());
            assertEquals(1, db.count("t_incident"));
            assertEquals(2, db.count("t_incident_timeline"));
            assertEquals(2, db.count("t_incident_outbox"));
        }
    }
}
