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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.security.AlertQueryAuth;
import com.nageoffer.ai.ragent.incident.alert.security.SourceHmacVerifier;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReceiveService;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReplayService;
import com.nageoffer.ai.ragent.incident.alert.web.AlertController;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.user.config.SaTokenConfig;
import com.nageoffer.ai.ragent.user.config.UserContextInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlertQueryAuthContractTest {
    @Test
    void globalExclusionIsNarrowAndExcludedGetStillRequiresLocalCredentials() {
        assertTrue(SaTokenConfig.isIncidentLocalAuthRoute(new MockHttpServletRequest("POST", "/incident/v1/alerts")));
        assertTrue(SaTokenConfig.isIncidentLocalAuthRoute(new MockHttpServletRequest("GET", "/incident/v1/alerts/123")));
        assertFalse(SaTokenConfig.isIncidentLocalAuthRoute(new MockHttpServletRequest("POST", "/incident/v1/alerts/123/replay")));
        assertFalse(SaTokenConfig.isIncidentLocalAuthRoute(new MockHttpServletRequest("GET", "/incident/v1/alerts/%31")));
        var auth = new AlertQueryAuth(mock(SourceHmacVerifier.class));
        var noCredential = new MockHttpServletRequest("GET", "/incident/v1/alerts/123");
        assertEquals(401, assertThrows(AlertQueryAuth.QueryAuthException.class,
                () -> auth.authenticate(noCredential, "123")).status());
    }

    @Test
    void configuredGlobalInterceptorsAllowMachineRouteOnlyToReachLocalAuth() throws Exception {
        var registry = new ExposedRegistry();
        new SaTokenConfig(mock(UserContextInterceptor.class)).addInterceptors(registry);
        var mvc = MockMvcBuilders.standaloneSetup(new AlertController(mock(AlertReceiveService.class),
                        new AlertQueryAuth(mock(SourceHmacVerifier.class)), mock(AlertMapper.class),
                        mock(AlertReplayService.class)))
                .setControllerAdvice(new IncidentErrorAdvice())
                .addInterceptors(registry.handlers())
                .build();
        mvc.perform(get("/incident/v1/alerts/123"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void sourceOwnerCanReadButCrossSourceAndUnknownIdAreIndistinguishable() throws Exception {
        var verifier = mock(SourceHmacVerifier.class);
        when(verifier.verify(any(), any(), any())).thenReturn(new SourceHmacVerifier.Identity("source-a", 1));
        var mapper = mock(AlertMapper.class);
        var row = new AlertMapper.AlertRow();
        row.setId("123");
        row.setSourceId("source-a");
        row.setCorrelationId("321");
        row.setProcessingStatus("RECEIVED");
        when(mapper.findById("123")).thenReturn(row);
        var mvc = MockMvcBuilders.standaloneSetup(new AlertController(mock(AlertReceiveService.class),
                        new AlertQueryAuth(verifier), mapper, mock(AlertReplayService.class)))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        mvc.perform(get("/incident/v1/alerts/123").header("X-Incident-Source-Id", "source-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.alertId").value("123"));
        row.setSourceId("source-b");
        mvc.perform(get("/incident/v1/alerts/123").header("X-Incident-Source-Id", "source-a"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        when(mapper.findById("123")).thenReturn(null);
        mvc.perform(get("/incident/v1/alerts/123").header("X-Incident-Source-Id", "source-a"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void mixedCredentialsAreRejectedAndRawStaffTokenRequiresAdminRole() {
        var auth = new AlertQueryAuth(mock(SourceHmacVerifier.class));
        var mixed = new MockHttpServletRequest("GET", "/incident/v1/alerts/123");
        mixed.addHeader("Authorization", "raw-token");
        mixed.addHeader("X-Incident-Source-Id", "source");
        assertEquals(401, assertThrows(AlertQueryAuth.QueryAuthException.class,
                () -> auth.authenticate(mixed, "123")).status());
        var staff = new MockHttpServletRequest("GET", "/incident/v1/alerts/123");
        staff.addHeader("Authorization", "raw-token");
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getLoginIdByToken("raw-token")).thenReturn("u-1");
            stp.when(() -> StpUtil.hasRole("u-1", "admin")).thenReturn(false);
            assertEquals(403, assertThrows(AlertQueryAuth.QueryAuthException.class,
                    () -> auth.authenticate(staff, "123")).status());
            stp.when(() -> StpUtil.hasRole("u-1", "admin")).thenReturn(true);
            assertTrue(auth.authenticate(staff, "123").staff());
        }
    }

    private static final class ExposedRegistry extends InterceptorRegistry {
        HandlerInterceptor[] handlers() {
            return getInterceptors().stream().map(HandlerInterceptor.class::cast)
                    .toArray(HandlerInterceptor[]::new);
        }
    }
}
