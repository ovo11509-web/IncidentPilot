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
import com.nageoffer.ai.ragent.incident.diagnosis.service.*;
import org.junit.jupiter.api.Test;
class IncidentSnapshotIntegrationTest {
    @Test void onlyAssociatedProcessedAlertsAndWatermarkIndependentDigest() throws Exception {
        try(var db=new DiagnosisDbFixture()) {
            db.seedIncident("100");var snapshots=new IncidentSnapshotService(db.alerts);
            assertEquals(0,db.guard.inIncident("100",snapshots::capture).path("alerts").size());
            db.associate();var first=db.guard.inIncident("100",snapshots::capture);
            assertEquals(1,first.path("alerts").size());assertFalse(first.path("truncated").asBoolean());
            assertEquals("ALERT_SOURCE_REPORT",first.path("facts").get(0).path("origin").asText());
            db.guard.inIncident("100",l->{l.reserveTimeline(1);return null;});
            var second=db.guard.inIncident("100",snapshots::capture);
            assertEquals(first.path("factsDigest"),second.path("factsDigest"));
            assertNotEquals(first.path("snapshotDigest"),second.path("snapshotDigest"));
            db.sql("UPDATE t_incident SET version=version+1 WHERE id='100'");
            assertEquals(first.path("factsDigest"),db.guard.inIncident("100",snapshots::capture).path("factsDigest"));
            db.sql("UPDATE t_incident_alert SET title='健康检查失败 token=never-persist-this-secret Authorization: Bearer secret-bearer password=\"two words\"' WHERE id='11'");
            assertFalse(db.guard.inIncident("100",snapshots::capture).toString().contains("never-persist-this-secret"));
            String safe=db.guard.inIncident("100",snapshots::capture).toString();assertFalse(safe.contains("secret-bearer"));assertFalse(safe.contains("two words"));
            db.sql("INSERT INTO t_incident_alert SELECT (1000+g)::text,source_id,'extra-'||g,protocol_version,payload_json,payload_digest,event_type,occurred_at,rule_id,title,severity,environment,service,resource_type,resource_id,summary,labels_json,fingerprint,aggregation_key,received_at,correlation_id,processing_status,processing_epoch,attempt_count,retry_count,next_attempt_at,last_failure_code,last_failure_summary,failed_at,incident_id,(2000+g)::text,attached_at,version,created_at,updated_at FROM t_incident_alert CROSS JOIN generate_series(1,101) g WHERE id='11'");
            db.sql("UPDATE t_incident SET alert_count=102 WHERE id='100'");var capped=db.guard.inIncident("100",snapshots::capture);
            assertTrue(capped.path("truncated").asBoolean());assertEquals(100,capped.path("alerts").size());
        }
    }
}
