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

package com.nageoffer.ai.ragent.mcp.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProtectedServiceAuthenticationTest {
    @Test
    void acceptsOnlyOutboundCredentialIncludingRotation() {
        var auth = new ProtectedServiceAuthentication(Map.of("INCIDENT_MCP_OUTBOUND_TOKEN", "o".repeat(32),
                "INCIDENT_MCP_OUTBOUND_TOKEN_PREVIOUS", "p".repeat(32),
                "INCIDENT_MCP_CALLBACK_TOKEN", "c".repeat(32)));
        assertTrue(auth.isAuthenticated("Bearer " + "o".repeat(32)));
        assertTrue(auth.isAuthenticated("Bearer " + "p".repeat(32)));
        assertFalse(auth.isAuthenticated("Bearer " + "c".repeat(32)));
        assertFalse(new ProtectedServiceAuthentication(Map.of()).isAuthenticated("Bearer " + "o".repeat(32)));
    }

    @Test
    void requiresTlsOffLoopbackAndHidesSensitiveHeaders() {
        ProtectedServiceAuthentication.requireSafeEndpoint(URI.create("http://localhost:9090/incident/internal"));
        ProtectedServiceAuthentication.requireSafeEndpoint(URI.create("https://app.example.test/incident/internal"));
        assertThrows(IllegalArgumentException.class,
                () -> ProtectedServiceAuthentication.requireSafeEndpoint(URI.create("http://app.example.test/api")));
        assertTrue(ProtectedServiceAuthentication.isSensitiveHeader("Authorization"));
        assertFalse(ProtectedServiceAuthentication.isSensitiveHeader("Content-Type"));
    }
}
