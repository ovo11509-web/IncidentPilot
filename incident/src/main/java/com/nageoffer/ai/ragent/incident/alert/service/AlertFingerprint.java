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

package com.nageoffer.ai.ragent.incident.alert.service;

import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer.CanonicalAlert;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Fingerprint identity is independent of title, source occurrence time, labels and severity. */
@Component
public final class AlertFingerprint {
    public Fingerprint of(String sourceId, CanonicalAlert alert) {
        var bytes = new ByteArrayOutputStream();
        for (String value : new String[] {"alert-fingerprint-v1", sourceId.toLowerCase(Locale.ROOT),
                alert.environment().toLowerCase(Locale.ROOT), alert.service().toLowerCase(Locale.ROOT),
                alert.resourceType().toLowerCase(Locale.ROOT), alert.resourceId(),
                alert.ruleId().toLowerCase(Locale.ROOT)}) {
            byte[] part = value.getBytes(StandardCharsets.UTF_8);
            bytes.writeBytes(ByteBuffer.allocate(4).putInt(part.length).array());
            bytes.writeBytes(part);
        }
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
            return new Fingerprint(digest, "alert-incident-v1:" + digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Fingerprint(String digest, String aggregationKey) {}
}
