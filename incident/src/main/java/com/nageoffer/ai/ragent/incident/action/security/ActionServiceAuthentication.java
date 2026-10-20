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

package com.nageoffer.ai.ragent.incident.action.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Authenticates only MCP-to-Incident callbacks; personnel sessions cannot satisfy this check. */
@Component
public final class ActionServiceAuthentication {
    private final Map<String, String> environment;

    public ActionServiceAuthentication() {
        this(System.getenv());
    }

    ActionServiceAuthentication(Map<String, String> environment) {
        this.environment = environment;
    }

    public boolean isAuthenticated(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= 7 || authorization.substring(7).isBlank()) {
            return false;
        }
        var supplied = authorization.substring(7);
        return matches(supplied, environment.get("INCIDENT_MCP_CALLBACK_TOKEN"))
                | matches(supplied, environment.get("INCIDENT_MCP_CALLBACK_TOKEN_PREVIOUS"));
    }

    private static boolean matches(String supplied, String expected) {
        return expected != null && expected.length() >= 32 && MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    public static void requireSafeEndpoint(URI uri) {
        var host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getFragment() != null
                || !("https".equalsIgnoreCase(uri.getScheme())
                        || ("http".equalsIgnoreCase(uri.getScheme()) && isLoopback(host)))) {
            throw new IllegalArgumentException("service endpoint requires TLS or local loopback");
        }
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)
                || "[::1]".equals(host);
    }

    public static boolean isSensitiveHeader(String name) {
        return "authorization".equalsIgnoreCase(name) || "x-action-ticket".equalsIgnoreCase(name);
    }
}
