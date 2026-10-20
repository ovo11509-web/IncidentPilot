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
import io.opentelemetry.api.trace.SpanKind;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class IncidentRequestObservationFilter extends OncePerRequestFilter {
    private final ActionServiceAuthentication authentication;
    public IncidentRequestObservationFilter(ActionServiceAuthentication authentication) { this.authentication = authentication; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR
                && request.getAttribute(ObservationScope.REQUEST_ID_ATTRIBUTE) != null) return false;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/incident/v1/") && !path.startsWith("/incident/internal/v1/");
    }
    @Override protected boolean shouldNotFilterErrorDispatch() { return false; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = ObservationScope.requestId(request);
        response.setHeader("X-Request-Id", id);
        boolean trusted = request.getRequestURI().substring(request.getContextPath().length()).startsWith("/incident/internal/v1/")
                && authentication.isAuthenticated(request.getHeader("Authorization"));
        try (var scope = ObservationScope.root("incident.http", SpanKind.SERVER, ObservationScope.extract(request, trusted))) {
            scope.id("requestId", id);
            String trace = scope.fields().get("traceId");
            if (trace != null) response.setHeader("X-Trace-Id", trace);
            boolean completed = false;
            try { chain.doFilter(request, response); completed = true; }
            finally { scope.outcome(!completed ? "HTTP_ERROR" : response.getStatus() < 400 ? "HTTP_SUCCESS" : "HTTP_REJECTED"); }
        }
    }
}
