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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.core.dao.OperationsMapper;
import com.nageoffer.ai.ragent.incident.core.web.IncidentOperationsController;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OperationsSecurityTest {
    @Test
    void unauthenticatedOperationsRouteIsRejectedBeforeReadingDatabase() throws Exception {
        var mapper = mock(OperationsMapper.class);
        var mvc = MockMvcBuilders.standaloneSetup(new IncidentOperationsController(mapper))
                .setControllerAdvice(new IncidentErrorAdvice()).build();
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::checkLogin).thenThrow(new NotLoginException("not logged in", "not_token", "login"));
            mvc.perform(get("/incident/v1/operations/status"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
        try (var stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.checkRole("admin")).thenThrow(new NotRoleException("admin"));
            mvc.perform(get("/incident/v1/operations/status"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        verifyNoInteractions(mapper);
    }
}
