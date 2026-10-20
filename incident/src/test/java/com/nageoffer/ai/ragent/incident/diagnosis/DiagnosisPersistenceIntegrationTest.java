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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class DiagnosisPersistenceIntegrationTest {
    @Test
    void upgradePreservesExistingTypesAndProtectsFrozenColumns() throws Exception {
        try (var db = new PostgreSQLContainer("postgres:16-alpine")) {
            db.start();
            try (var c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                 var s = c.createStatement()) {
                for (String name : new String[]{"260923_incident_core.sql", "260923_incident_safe_action.sql",
                        "261003_incident_diagnosis.sql"}) {
                    s.execute(Files.readString(Path.of("..", "resources/database/upgrades/v2.0.0", name)));
                }
                try (var rows = s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE 't_incident_diagnosis_%'")) {
                    rows.next(); assertEquals(3, rows.getInt(1));
                }
                s.execute("SET ROLE ragent_app");
                assertThrows(java.sql.SQLException.class, () -> s.execute("UPDATE t_incident_diagnosis_request SET snapshot_json='{}'"));
                assertThrows(java.sql.SQLException.class, () -> s.execute("DELETE FROM t_incident_diagnosis_result"));
                assertThrows(java.sql.SQLException.class, () -> s.execute("UPDATE t_incident_diagnosis_action_link SET actor_id='tampered'"));
                s.execute("UPDATE t_incident_diagnosis_request SET state='COMPLETED', version=version+1, completed_at=clock_timestamp() WHERE false");
            }
        }
    }
}
