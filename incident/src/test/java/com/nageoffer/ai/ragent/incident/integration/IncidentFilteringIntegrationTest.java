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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.core.service.EpisodePartitioner;
import com.nageoffer.ai.ragent.incident.core.service.IncidentQueryService;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import com.nageoffer.ai.ragent.incident.core.web.IncidentController;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IncidentFilteringIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void filtersRequireOneProcessedMemberAndTreatSearchAsLiteral() throws Exception {
        try (var db = new IncidentDbFixture(); var stp = mockStatic(StpUtil.class)) {
            var mvc = mvc(db.incidents, db.alerts);
            var time = OffsetDateTime.now().minusMinutes(5);
            String incident = add(db, "10", 'a', time, "API quota 90%_safe", "HIGH", "api", "prod");
            add(db, "11", 'a', time.plusSeconds(1), "critical lane", "CRITICAL", "api", "prod");
            add(db, "20", 'b', time.plusSeconds(2), "API quota 90XaSafe", "HIGH", "web", "stage");
            assertEquals(1, data(mvc, list().param("q", "90%_safe")).get("items").size());
            assertEquals(incident, data(mvc, list().param("q", "API QUOTA").param("service", " API ")
                    .param("environment", " PROD ").param("severity", "high")).at("/items/0/incidentId").asText());
            assertEquals(0, data(mvc, list().param("q", "critical lane").param("severity", "HIGH")).get("items").size());
            assertEquals(1, data(mvc, list().param("q", "critical lane").param("severity", "CRITICAL")).get("items").size());
            assertEquals(0, data(mvc, list().param("q", "' OR 1=1 --")).get("items").size());

            db.insertAlert("30", "alert-incident-v1:" + "a".repeat(64), time.plusSeconds(3));
            db.sql("UPDATE t_incident_alert SET title='not yet attached' WHERE id='30'");
            assertEquals(0, data(mvc, list().param("q", "not yet attached")).get("items").size());
            db.sql("UPDATE t_incident_alert SET processing_status='FAILED', last_failure_code='FIXTURE',"
                    + " failed_at=clock_timestamp() WHERE id IN ('10','11')");
            var noMembers = data(mvc, list().param("q", incident));
            assertEquals(1, noMembers.get("items").size());
            assertTrue(noMembers.at("/items/0/summary").isNull());
            assertEquals(0, data(mvc, list().param("q", incident).param("service", "api")).get("items").size());
        }
    }

    @Test void summariesAreSafeAnchorProjectionsAndFilteringPrecedesStableKeysets() throws Exception {
        try (var db = new IncidentDbFixture(); var stp = mockStatic(StpUtil.class)) {
            var mvc = mvc(db.incidents, db.alerts);
            var time = OffsetDateTime.now().minusMinutes(5);
            String older = add(db, "10", 'a', time, "first password=fixture-marker", "HIGH", "api", "prod");
            add(db, "11", 'a', time.plusSeconds(1), "later critical", "CRITICAL", "api", "prod");
            String unrelated = add(db, "20", 'b', time.plusSeconds(3), "other", "HIGH", "web", "stage");
            String newer = add(db, "30", 'c', time.plusSeconds(3), "Unicode café", "LOW", "api", "prod");
            var first = data(mvc, list().param("limit", "1").param("service", "api"));
            assertEquals(newer, first.at("/items/0/incidentId").asText());
            assertTrue(first.get("hasMore").asBoolean());
            var cursor = first.get("nextCursor");
            var second = data(mvc, list().param("limit", "1").param("service", "api")
                    .param("afterEpisodeStartAt", cursor.get("afterEpisodeStartAt").asText())
                    .param("afterId", cursor.get("afterId").asText()));
            assertEquals(older, second.at("/items/0/incidentId").asText());
            assertFalse(second.get("hasMore").asBoolean());
            assertTrue(second.get("nextCursor").isNull());
            var summary = second.at("/items/0/summary");
            assertEquals(4, summary.size());
            assertEquals("HIGH", summary.get("severity").asText());
            assertEquals("api", summary.get("service").asText());
            assertEquals("prod", summary.get("environment").asText());
            assertFalse(summary.toString().contains("fixture-marker"));
            assertFalse(summary.toString().contains("payload"));
            assertFalse(summary.toString().contains("labels"));
            var laterMatch = data(mvc, list().param("q", "later critical"));
            assertEquals("HIGH", laterMatch.at("/items/0/summary/severity").asText());
            assertEquals(1, data(mvc, list().param("q", "cafe\u0301")).get("items").size());
            assertEquals(3, data(mvc, list().param("q", "   ").param("service", "   ")).get("items").size());
            var seen = new java.util.ArrayList<String>();
            JsonNode next = null;
            do {
                var request = list().param("limit", "1");
                if (next != null) request.param("afterEpisodeStartAt", next.get("afterEpisodeStartAt").asText())
                        .param("afterId", next.get("afterId").asText());
                var data = data(mvc, request);
                seen.add(data.at("/items/0/incidentId").asText()); next = data.get("nextCursor");
                assertTrue(seen.size() <= 3, "Keyset must advance without duplicate rows");
            } while (!next.isNull());
            assertEquals(List.of(newer, unrelated, older), seen);

            // A legacy/corrupt anchor that belongs to another incident must not project that title.
            db.sql("UPDATE t_incident SET episode_anchor_alert_id='20' WHERE id='" + older + "'");
            assertTrue(data(mvc, list().param("q", older)).at("/items/0/summary").isNull());
            assertNotEquals(older, unrelated);
        }
    }

    @Test void invalidFiltersFailBeforeQueriesAndRoleDenialKeepsReadBoundary() throws Exception {
        var incidents = mock(IncidentMapper.class);
        var alerts = mock(AlertMapper.class);
        var mvc = mvc(incidents, alerts);
        try (var stp = mockStatic(StpUtil.class)) {
            for (String name : List.of("q", "service", "environment")) {
                mvc.perform(list().param(name, "x".repeat(129))).andExpect(status().isUnprocessableEntity());
                mvc.perform(list().param(name, "bad\ninput")).andExpect(status().isUnprocessableEntity());
                mvc.perform(list().param(name, "x".repeat(128))).andExpect(status().isOk());
            }
            mvc.perform(list().param("severity", "URGENT")).andExpect(status().isUnprocessableEntity());
            assertEquals(3, mockingDetails(incidents).getInvocations().size());
            verifyNoInteractions(alerts); clearInvocations(incidents);
            stp.when(() -> StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            mvc.perform(list().param("q", "x")).andExpect(status().isForbidden());
            verifyNoInteractions(incidents, alerts);
        }
    }

    private static MockHttpServletRequestBuilder list() { return get("/incident/v1/incidents"); }
    private JsonNode data(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");
    }
    private static MockMvc mvc(IncidentMapper incidents, AlertMapper alerts) {
        var controller = new IncidentController(incidents, alerts, mock(TimelineMapper.class),
                mock(IncidentTransitionService.class), new EpisodePartitioner());
        controller.setQueryService(new IncidentQueryService(incidents, alerts));
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new IncidentErrorAdvice()).build();
    }
    private static String add(IncidentDbFixture db, String id, char key, OffsetDateTime time,
            String title, String severity, String service, String environment) throws Exception {
        db.insertAlert(id, "alert-incident-v1:" + String.valueOf(key).repeat(64), time);
        db.sql("UPDATE t_incident_alert SET title='" + title.replace("'", "''") + "',severity='" + severity
                + "',service='" + service + "',environment='" + environment + "' WHERE id='" + id + "'");
        db.aggregation.processOne(id);
        return db.alerts.findById(id).getIncidentId();
    }
}
