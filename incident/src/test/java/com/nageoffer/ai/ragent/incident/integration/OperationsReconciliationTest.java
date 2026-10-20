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

import static org.junit.jupiter.api.Assertions.*;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class OperationsReconciliationTest {
    @Test
    void countersAndAgesMatchCurrentRowsWithoutExposingPayload() throws Exception {
        try (var db = new IncidentDbFixture()) {
            db.insertAlert("3001", "alert-incident-v1:" + "c".repeat(64), OffsetDateTime.now().minusSeconds(5));
            db.insertAlert("3002", "alert-incident-v1:" + "d".repeat(64), OffsetDateTime.now().minusSeconds(5));
            db.sql("UPDATE t_incident_alert SET retry_count = 1, last_failure_code = 'PROCESSING_FAILED', "
                    + "next_attempt_at = clock_timestamp() - interval '1 second' WHERE id = '3002'");
            db.sql("UPDATE t_incident_alert SET payload_json = '{\"probe\":\"SECRET_PROBE\"}'::jsonb "
                    + "WHERE id = '3002'");
            var snapshot = db.operations.snapshot();
            assertEquals(db.count("t_incident_alert WHERE processing_status = 'RECEIVED'"),
                    snapshot.getReceivedBacklogCount());
            assertEquals(2, snapshot.getReceivedBacklogCount());
            assertEquals(1, snapshot.getDueRetryCount());
            assertTrue(snapshot.getOldestReceivedAgeSeconds() >= 4);
            assertEquals(0, snapshot.getFailedAlertCount());
            assertEquals(0, snapshot.getPendingOutboxCount());
            assertNull(snapshot.getOldestPendingOutboxAgeSeconds());
            assertEquals(1, db.operations.recentFailures().size());
            assertEquals("PROCESSING_FAILED", db.operations.recentFailures().get(0).getCode());
            assertFalse(db.operations.recentFailures().toString().contains("SECRET_PROBE"));
        }
    }
}
