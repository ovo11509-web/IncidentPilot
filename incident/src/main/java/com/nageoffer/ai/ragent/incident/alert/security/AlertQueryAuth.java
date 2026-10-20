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

package com.nageoffer.ai.ragent.incident.alert.security;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;
import org.springframework.stereotype.Component;

/** Local authentication for the one route excluded from the global login interceptor. */
@Component
public final class AlertQueryAuth {
    private final SourceHmacVerifier verifier;

    public AlertQueryAuth(SourceHmacVerifier verifier) {
        this.verifier = verifier;
    }

    public Principal authenticate(HttpServletRequest request, String alertId) {
        String expected = "/incident/v1/alerts/" + alertId;
        String actual = request.getRequestURI().substring(request.getContextPath().length());
        if (!alertId.matches("[0-9]{1,20}") || !expected.equals(actual) || request.getQueryString() != null)
            throw new QueryAuthException(400);
        try {
            if (request.getInputStream().read() != -1) throw new QueryAuthException(401);
        } catch (IOException e) {
            throw new QueryAuthException(401);
        }
        boolean sourceHeaders = Collections.list(request.getHeaderNames()).stream()
                .anyMatch(name -> name.regionMatches(true, 0, "X-Incident-", 0, "X-Incident-".length()));
        var authorization = Collections.list(request.getHeaders("Authorization"));
        if (sourceHeaders == !authorization.isEmpty()) throw new QueryAuthException(401);
        if (sourceHeaders) {
            try {
                var source = verifier.verify(request, new byte[0], expected);
                if (source.protocolVersion() != 1) throw new QueryAuthException(422);
                return new Principal(source.sourceId(), false);
            } catch (SourceHmacVerifier.AuthenticationException e) {
                throw new QueryAuthException(401);
            }
        }
        if (authorization.size() != 1 || authorization.get(0).isBlank()
                || authorization.get(0).chars().anyMatch(Character::isWhitespace)) throw new QueryAuthException(401);
        try {
            Object loginId = StpUtil.getLoginIdByToken(authorization.get(0));
            if (loginId == null) throw new QueryAuthException(401);
            if (!StpUtil.hasRole(loginId, "admin")) throw new QueryAuthException(403);
            return new Principal(null, true);
        } catch (QueryAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new QueryAuthException(401);
        }
    }

    public record Principal(String sourceId, boolean staff) {}
    public static final class QueryAuthException extends RuntimeException {
        private final int status;
        public QueryAuthException(int status) { this.status = status; }
        public int status() { return status; }
    }
}
