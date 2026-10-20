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

package com.nageoffer.ai.ragent.incident.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.core.service.EpisodePartitioner;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import com.nageoffer.ai.ragent.incident.core.web.IncidentController;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IncidentControllerContractTest {
    private static final String KEY = "alert-incident-v1:" + "b".repeat(64);

    @Test
    void staffCanReadSafeSnapshotAndOrderedTimelineAndTransitionWithRealHttpStatus() throws Exception {
        var fixture = fixture();
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("42");
            fixture.mvc().perform(get("/incident/v1/incidents/100"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.incidentId").value("100"))
                    .andExpect(jsonPath("$.data.lifecycleState").value("OPEN"));
            fixture.mvc().perform(get("/incident/v1/incidents/100/timeline").param("limit", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].sequenceNo").value(1))
                    .andExpect(jsonPath("$.data.nextAfterSequence").value(1));
            fixture.mvc().perform(post("/incident/v1/incidents/100/transitions")
                            .contentType("application/json")
                            .content("{\"expectedState\":\"OPEN\",\"expectedVersion\":0,"
                                    + "\"targetState\":\"ACKNOWLEDGED\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("0"));
            fixture.mvc().perform(post("/incident/v1/incidents/100/transitions")
                            .contentType("application/json")
                            .content("{\"expectedState\":\"OPEN\",\"expectedVersion\":0,"
                                    + "\"targetState\":\"ACKNOWLEDGED\",\"extra\":1}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    void staffRoutesReturnActualUnauthorizedAndForbiddenStatuses() throws Exception {
        var fixture = fixture();
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin).thenThrow(new NotLoginException("not logged in", "not_token", "login"));
            fixture.mvc().perform(get("/incident/v1/incidents/100"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            fixture.mvc().perform(get("/incident/v1/incidents/100"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    private Fixture fixture() {
        var incidents = mock(IncidentMapper.class);
        var alerts = mock(AlertMapper.class);
        var timeline = mock(TimelineMapper.class);
        var transitions = mock(IncidentTransitionService.class);
        var row = new IncidentMapper.IncidentRow();
        row.setId("100");
        row.setAggregationKey(KEY);
        row.setAggregationRuleVersion("alert-incident-v1");
        row.setAggregationWindowSeconds(1800);
        row.setEpisodeStartAt(OffsetDateTime.parse("2026-09-23T00:00:00Z"));
        row.setEpisodeAnchorAlertId("10");
        row.setFirstAlertReceivedAt(row.getEpisodeStartAt());
        row.setLastAlertReceivedAt(row.getEpisodeStartAt());
        row.setAlertCount(1);
        row.setLifecycleState("OPEN");
        when(incidents.findById("100")).thenReturn(row);
        when(incidents.findAllByAggregationKey(KEY)).thenReturn(List.of(row));
        when(incidents.databaseNow()).thenReturn(row.getEpisodeStartAt().plusSeconds(1));
        var alert = new AlertMapper.AlertRow();
        alert.setId("10");
        alert.setReceivedAt(row.getEpisodeStartAt());
        alert.setProcessingStatus("PROCESSED");
        when(alerts.findAllByAggregationKey(KEY)).thenReturn(List.of(alert));
        var fact = new TimelineMapper.Fact();
        fact.setId("11");
        fact.setIncidentId("100");
        fact.setSequenceNo(1);
        fact.setEventType("INCIDENT_CREATED");
        fact.setActorType("SYSTEM");
        fact.setActorId("incident-worker");
        fact.setOccurredAt(row.getEpisodeStartAt());
        fact.setCorrelationId("99");
        fact.setSummaryJson("{}");
        when(timeline.findPage("100", 0, 2)).thenReturn(List.of(fact));
        when(transitions.transition(anyString(), anyString(), anyLong(), anyString(), any(), anyString(), anyString()))
                .thenReturn(row);
        var mvc = MockMvcBuilders.standaloneSetup(new IncidentController(incidents, alerts, timeline,
                        transitions, new EpisodePartitioner()))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        return new Fixture(mvc);
    }

    private record Fixture(org.springframework.test.web.servlet.MockMvc mvc) {}
}
