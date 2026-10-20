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

import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Verifies the exact seven-frame HMAC contract before JSON is decoded. */
@Component
public final class SourceHmacVerifier {
    private static final Pattern VERSION = Pattern.compile("(?:0|[1-9][0-9]{0,2})");
    private static final Pattern SOURCE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern TIMESTAMP = Pattern.compile("(?:0|[1-9][0-9]*)");
    private static final Pattern SIGNATURE = Pattern.compile("sha256=[0-9a-f]{64}");
    private final IncidentProperties properties;
    private final Function<String, String> secrets;
    private final Clock clock;

    @Autowired
    public SourceHmacVerifier(IncidentProperties properties) {
        this(properties, System::getenv, Clock.systemUTC());
    }

    public SourceHmacVerifier(IncidentProperties properties, Function<String, String> secrets, Clock clock) {
        this.properties = properties;
        this.secrets = secrets;
        this.clock = clock;
    }

    public Identity verify(HttpServletRequest request, byte[] rawBody, String canonicalPath) {
        try {
            if (request.getHeaders("Authorization").hasMoreElements()) throw new AuthenticationException();
            String version = single(request, "X-Incident-Protocol-Version", VERSION);
            String source = single(request, "X-Incident-Source-Id", SOURCE);
            String keyId = single(request, "X-Incident-Key-Id", KEY_ID);
            String epochText = single(request, "X-Incident-Request-Timestamp", TIMESTAMP);
            String signature = single(request, "X-Incident-Signature", SIGNATURE);
            long epoch = Long.parseLong(epochText);
            Instant time = Instant.ofEpochSecond(epoch);
            Instant now = clock.instant();
            if (time.isBefore(now.minus(properties.getIngress().getSignatureSkew()))
                    || time.isAfter(now.plus(properties.getIngress().getSignatureSkew())))
                throw new AuthenticationException();
            var configuredSource = properties.getSources().get(source);
            if (configuredSource == null) throw new AuthenticationException();
            var key = configuredSource.getKeys().stream()
                    .filter(item -> item.isEnabled() && Objects.equals(item.getKeyId(), keyId))
                    .findFirst().orElseThrow(AuthenticationException::new);
            String secret = secrets.apply(key.getSecretEnvironmentVariable());
            if (secret == null || secret.isEmpty()) throw new AuthenticationException();
            byte[] expected = hmac(secret.getBytes(StandardCharsets.UTF_8),
                    frame(version, source, epochText, request.getMethod().toUpperCase(java.util.Locale.ROOT),
                            canonicalPath, rawBody));
            byte[] actual = HexFormat.of().parseHex(signature.substring(7));
            if (!MessageDigest.isEqual(expected, actual)) throw new AuthenticationException();
            return new Identity(source, Integer.parseInt(version));
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthenticationException();
        }
    }

    private static String single(HttpServletRequest request, String name, Pattern pattern) {
        var all = Collections.list(request.getHeaders(name));
        if (all.size() != 1) throw new AuthenticationException();
        String value = all.get(0);
        if (!pattern.matcher(value).matches() || value.chars().anyMatch(c -> c > 127))
            throw new AuthenticationException();
        return value;
    }

    /** Version marker is prepended as the first length-prefixed component. */
    public static byte[] frame(String version, String source, String timestamp, String method,
            String path, byte[] body) {
        var out = new ByteArrayOutputStream();
        for (byte[] part : new byte[][] {
                "incident-hmac-v1".getBytes(StandardCharsets.US_ASCII),
                version.getBytes(StandardCharsets.US_ASCII), source.getBytes(StandardCharsets.US_ASCII),
                timestamp.getBytes(StandardCharsets.US_ASCII), method.getBytes(StandardCharsets.US_ASCII),
                path.getBytes(StandardCharsets.US_ASCII), body}) {
            out.writeBytes(ByteBuffer.allocate(4).putInt(part.length).array());
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    public static byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    public record Identity(String sourceId, int protocolVersion) {}
    public static final class AuthenticationException extends RuntimeException {}
}
