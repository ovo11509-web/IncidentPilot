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

package com.nageoffer.ai.ragent.incident.observability;

import com.nageoffer.ai.ragent.framework.observability.ObservationScope;
import com.nageoffer.ai.ragent.incident.action.security.ActionServiceAuthentication;
import com.nageoffer.ai.ragent.incident.alert.web.AlertController;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IncidentRequestIdentityTest {
    @Test void successAndErrorDispatchReuseServerAttemptAcrossContextPath() throws Exception {
        var filter = new IncidentRequestObservationFilter(mock(ActionServiceAuthentication.class));
        var request = new MockHttpServletRequest("GET", "/api/ragent/incident/v1/incidents");
        request.setContextPath("/api/ragent"); request.addHeader("X-Request-Id", "injected");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (r,s) -> {
            assertThat(AlertController.requestId(request)).isEqualTo(response.getHeader("X-Request-Id"));
            assertThat(ObservationScope.requestId()).isEqualTo(response.getHeader("X-Request-Id"));
        });
        String id = response.getHeader("X-Request-Id");
        request.setDispatcherType(DispatcherType.ERROR);
        filter.doFilter(request, response, (r,s) -> assertThat(AlertController.requestId(request)).isEqualTo(id));
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(id).isNotEqualTo("injected");
        assertThat(response.getHeader("X-Trace-Id")).isNull();
    }

    @Test void earlyRejectKeepsRequestHeaderAndBodyId() throws Exception {
        var filter = new IncidentRequestObservationFilter(mock(ActionServiceAuthentication.class));
        var request = new MockHttpServletRequest("POST", "/incident/v1/alerts");
        var response = new MockHttpServletResponse();
        filter.doFilter(request,response,(r,s) -> {
            response.setStatus(401);
            response.getWriter().write("{\"requestId\":\"" + ObservationScope.requestId(request) + "\"}");
        });
        assertThat(response.getContentAsString()).contains(response.getHeader("X-Request-Id"));
    }
}
