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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.security.AlertQueryAuth;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReceiveService;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReplayService;
import com.nageoffer.ai.ragent.incident.alert.web.AlertController;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlertReplayContractTest {
    @Test
    void personnelOnlyReplayReturnsSafeHttpStatuses() throws Exception {
        var replay = mock(AlertReplayService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AlertController(mock(AlertReceiveService.class),
                        mock(AlertQueryAuth.class), mock(AlertMapper.class), replay))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        String url = "/incident/v1/alerts/123/replay";
        String body = "{\"expectedStatus\":\"FAILED\",\"expectedVersion\":2,\"reason\":\"safe\"}";
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin).thenThrow(new NotLoginException("not logged in", "not_token", "login"));
            mvc.perform(post(url).contentType("application/json").content(body))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(replay);
        }
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("operator");
            mvc.perform(post(url).contentType("application/json").content("{broken"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post(url).contentType("application/json").content("{}"))
                    .andExpect(status().isUnprocessableEntity());
            var current = new AlertMapper.AlertRow();
            current.setId("123");
            current.setProcessingStatus("FAILED");
            current.setVersion(3);
            when(replay.replay(eq("123"), eq(2L), eq("safe"), eq("operator"), anyString()))
                    .thenThrow(new AlertReplayService.ConflictException(current));
            mvc.perform(post(url).contentType("application/json").content(body))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.data.currentVersion").value(3));
            when(replay.replay(eq("123"), eq(2L), eq("safe"), eq("operator"), anyString()))
                    .thenThrow(new DataAccessResourceFailureException("SECRET_PROBE"));
            mvc.perform(post(url).contentType("application/json").content(body))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("PERSISTENCE_UNAVAILABLE"))
                    .andExpect(content().string(not(containsString("SECRET_PROBE"))));
            current.setVersion(4);
            current.setProcessingStatus("RECEIVED");
            current.setCorrelationId("789");
            when(replay.replay(eq("123"), eq(2L), eq("safe"), eq("operator"), anyString()))
                    .thenReturn(current);
            mvc.perform(post(url).contentType("application/json").content(body))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.processingStatus").value("RECEIVED"));
        }
    }
}
