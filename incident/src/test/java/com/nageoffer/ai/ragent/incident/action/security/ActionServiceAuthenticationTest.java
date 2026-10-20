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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionServiceAuthenticationTest {
    @Test
    void acceptsOnlyCallbackCredentialIncludingRotation() {
        var current = "c".repeat(32);
        var previous = "p".repeat(32);
        var auth = new ActionServiceAuthentication(Map.of("INCIDENT_MCP_CALLBACK_TOKEN", current,
                "INCIDENT_MCP_CALLBACK_TOKEN_PREVIOUS", previous,
                "INCIDENT_MCP_OUTBOUND_TOKEN", "o".repeat(32)));
        assertTrue(auth.isAuthenticated("Bearer " + current));
        assertTrue(auth.isAuthenticated("Bearer " + previous));
        assertFalse(auth.isAuthenticated("Bearer " + "o".repeat(32)));
        assertFalse(auth.isAuthenticated("Bearer " + "c".repeat(31)));
        assertFalse(auth.isAuthenticated(null));
        assertFalse(new ActionServiceAuthentication(Map.of()).isAuthenticated("Bearer " + current));
    }

    @Test
    void requiresTlsOffLoopbackAndHidesSensitiveHeaders() {
        ActionServiceAuthentication.requireSafeEndpoint(URI.create("http://127.0.0.1:9099/mcp/incident"));
        ActionServiceAuthentication.requireSafeEndpoint(URI.create("https://mcp.example.test/mcp/incident"));
        assertThrows(IllegalArgumentException.class,
                () -> ActionServiceAuthentication.requireSafeEndpoint(URI.create("http://mcp.example.test/mcp")));
        assertTrue(ActionServiceAuthentication.isSensitiveHeader("Authorization"));
        assertFalse(ActionServiceAuthentication.isSensitiveHeader("Accept"));
    }
}
