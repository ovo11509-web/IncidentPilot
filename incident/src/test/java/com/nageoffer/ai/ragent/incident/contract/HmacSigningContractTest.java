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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nageoffer.ai.ragent.incident.alert.security.SourceHmacVerifier;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class HmacSigningContractTest {
    private static final String POST_PATH = "/incident/v1/alerts";
    private static final long NOW = 1_700_000_000L;
    private static final byte[] BODY = "{}".getBytes(StandardCharsets.UTF_8);

    @Test
    void fixedCrossLanguageVectorUsesSevenLengthPrefixedComponents() {
        byte[] frame = SourceHmacVerifier.frame("1", "source", Long.toString(NOW), "POST", POST_PATH, BODY);
        assertEquals("00000010696e636964656e742d686d61632d7631000000013100000006736f757263650000000a3137303030303030303000000004504f5354000000132f696e636964656e742f76312f616c65727473000000027b7d",
                HexFormat.of().formatHex(frame));
        assertEquals("aca1b1d7f33640d6f0a8da5335cac12f37347890b4ccd22e859a9e98980a89f3",
                HexFormat.of().formatHex(SourceHmacVerifier.hmac("test-key".getBytes(StandardCharsets.UTF_8), frame)));
    }

    @Test
    void actualMethodPathTargetAndRawBytesAreBound() {
        var verifier = verifier();
        var request = signed("POST", POST_PATH, BODY, "1", "source", "primary", NOW, "test-key");
        assertEquals("source", verifier.verify(request, BODY, POST_PATH).sourceId());
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(request, "{ }".getBytes(StandardCharsets.UTF_8), POST_PATH));
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(request, new byte[0], POST_PATH));
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(request, BODY, "/incident/v1/alerts/1"));
        request.setMethod("GET");
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(request, BODY, POST_PATH));
        var query = signed("GET", "/incident/v1/alerts/1", new byte[0], "1", "source", "primary", NOW, "test-key");
        assertEquals("source", verifier.verify(query, new byte[0], "/incident/v1/alerts/1").sourceId());
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(query, new byte[0], "/incident/v1/alerts/2"));
    }

    @Test
    void inclusiveClockBoundariesAndOverlappingKeys() {
        var verifier = verifier();
        var oldEdge = signed("POST", POST_PATH, BODY, "1", "source", "primary", NOW - 300, "test-key");
        var newEdge = signed("POST", POST_PATH, BODY, "1", "source", "secondary", NOW + 300, "next-key");
        assertEquals(1, verifier.verify(oldEdge, BODY, POST_PATH).protocolVersion());
        assertEquals(1, verifier.verify(newEdge, BODY, POST_PATH).protocolVersion());
        var outside = signed("POST", POST_PATH, BODY, "1", "source", "primary", NOW - 301, "test-key");
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(outside, BODY, POST_PATH));
        var disabled = signed("POST", POST_PATH, BODY, "1", "source", "disabled", NOW, "old-key");
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(disabled, BODY, POST_PATH));
        var unknown = signed("POST", POST_PATH, BODY, "1", "source", "unknown", NOW, "test-key");
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(unknown, BODY, POST_PATH));
        var changedKeyId = signed("POST", POST_PATH, BODY, "1", "source", "primary", NOW, "test-key");
        changedKeyId.removeHeader("X-Incident-Key-Id");
        changedKeyId.addHeader("X-Incident-Key-Id", "secondary");
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(changedKeyId, BODY, POST_PATH));
    }

    @Test
    void noncanonicalHeadersTamperingAndMixedCredentialsFail() {
        var verifier = verifier();
        var request = signed("POST", POST_PATH, BODY, "1", "source", "primary", NOW, "test-key");
        request.addHeader("X-Incident-Key-Id", "primary");
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(request, BODY, POST_PATH));
        var leadingZero = signed("POST", POST_PATH, BODY, "1", "source", "primary", NOW, "test-key");
        leadingZero.removeHeader("X-Incident-Request-Timestamp");
        leadingZero.addHeader("X-Incident-Request-Timestamp", "0" + NOW);
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(leadingZero, BODY, POST_PATH));
        var mixed = signed("POST", POST_PATH, BODY, "1", "source", "primary", NOW, "test-key");
        mixed.addHeader("Authorization", "token");
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(mixed, BODY, POST_PATH));
        var uppercase = signed("POST", POST_PATH, BODY, "1", "source", "primary", NOW, "test-key");
        String digest = uppercase.getHeader("X-Incident-Signature");
        uppercase.removeHeader("X-Incident-Signature");
        uppercase.addHeader("X-Incident-Signature", digest.toUpperCase(java.util.Locale.ROOT));
        assertThrows(SourceHmacVerifier.AuthenticationException.class,
                () -> verifier.verify(uppercase, BODY, POST_PATH));
    }

    private SourceHmacVerifier verifier() {
        var properties = new IncidentProperties();
        var source = new IncidentProperties.Source();
        var first = key("primary", "PRIMARY", true);
        var second = key("secondary", "SECONDARY", true);
        var disabled = key("disabled", "DISABLED", false);
        source.setKeys(List.of(first, second, disabled));
        properties.setSources(Map.of("source", source));
        Map<String, String> secrets = Map.of("PRIMARY", "test-key", "SECONDARY", "next-key", "DISABLED", "old-key");
        return new SourceHmacVerifier(properties, secrets::get, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    private IncidentProperties.Key key(String id, String secretRef, boolean enabled) {
        var key = new IncidentProperties.Key();
        key.setKeyId(id);
        key.setSecretEnvironmentVariable(secretRef);
        key.setEnabled(enabled);
        return key;
    }

    private MockHttpServletRequest signed(String method, String path, byte[] body, String version,
            String source, String keyId, long epoch, String secret) {
        var request = new MockHttpServletRequest(method, path);
        request.addHeader("X-Incident-Protocol-Version", version);
        request.addHeader("X-Incident-Source-Id", source);
        request.addHeader("X-Incident-Key-Id", keyId);
        request.addHeader("X-Incident-Request-Timestamp", Long.toString(epoch));
        byte[] frame = SourceHmacVerifier.frame(version, source, Long.toString(epoch), method, path, body);
        request.addHeader("X-Incident-Signature", "sha256=" + HexFormat.of().formatHex(
                SourceHmacVerifier.hmac(secret.getBytes(StandardCharsets.UTF_8), frame)));
        return request;
    }
}
