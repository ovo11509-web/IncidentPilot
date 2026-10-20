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

package com.nageoffer.ai.ragent.incident.action.contract;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/** Versioned, unambiguous binding for the single protected simulation tool. */
public final class ActionBindingV1 {
    public static final String VERSION = "action-binding-v1";
    public static final String TOOL = "simulation.set-state";
    public static final String TOOL_VERSION = "1";
    public static final String TARGET_TYPE = "SIM_SERVICE";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final ObjectMapper JSON = mapper();

    private ActionBindingV1() {}

    public static String canonicalParameters(String rawJson) {
        try {
            var node = JSON.readTree(rawJson);
            if (node == null || !node.isObject() || node.size() != 1 || !node.has("desiredState")
                    || !node.get("desiredState").isTextual()) {
                throw new IllegalArgumentException("invalid simulation parameters");
            }
            var state = node.get("desiredState").textValue();
            if (!Set.of("READY", "PAUSED").contains(state)) {
                throw new IllegalArgumentException("invalid desiredState");
            }
            return "{\"desiredState\":\"" + state + "\"}";
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid simulation parameters");
        }
    }

    public static String proposalDigest(String incidentId, String sourceType, String sourceRequestId,
            String toolName, String toolVersion, String targetType, String targetId, String rawParameters) {
        validateToolTarget(toolName, toolVersion, targetType, targetId);
        requireId(incidentId, "incidentId");
        requireId(sourceRequestId, "sourceRequestId");
        if (!Set.of("USER", "AGENT").contains(sourceType)) {
            throw new IllegalArgumentException("invalid sourceType");
        }
        return digest(frame(Field.of(VERSION), Field.of(incidentId), Field.of(sourceType),
                Field.of(sourceRequestId), Field.of(toolName), Field.of(toolVersion),
                Field.of(targetType), Field.of(targetId), Field.of(canonicalParameters(rawParameters))));
    }

    public static String bindingDigest(String actionId, String incidentId, String toolName,
            String toolVersion, String targetType, String targetId, String rawParameters, String executionKey) {
        validateToolTarget(toolName, toolVersion, targetType, targetId);
        requireId(actionId, "actionId");
        requireId(incidentId, "incidentId");
        requireId(executionKey, "executionKey");
        return digest(frame(Field.of(VERSION), Field.of(actionId), Field.of(incidentId),
                Field.of(toolName), Field.of(toolVersion), Field.of(targetType), Field.of(targetId),
                Field.of(canonicalParameters(rawParameters)), Field.of(executionKey)));
    }

    static byte[] frame(Field... fields) {
        var out = new ByteArrayOutputStream();
        for (var field : fields) {
            if (!field.present()) {
                out.writeBytes(ByteBuffer.allocate(4).putInt(-2).array());
            } else if (field.value() == null) {
                out.writeBytes(ByteBuffer.allocate(4).putInt(-1).array());
            } else {
                var bytes = field.value().getBytes(StandardCharsets.UTF_8);
                out.writeBytes(ByteBuffer.allocate(4).putInt(bytes.length).array());
                out.writeBytes(bytes);
            }
        }
        return out.toByteArray();
    }

    static record Field(boolean present, String value) {
        static Field of(String value) {
            return new Field(true, value);
        }

        static Field missing() {
            return new Field(false, null);
        }
    }

    private static void validateToolTarget(String toolName, String toolVersion, String targetType, String targetId) {
        if (!TOOL.equals(toolName) || !TOOL_VERSION.equals(toolVersion) || !TARGET_TYPE.equals(targetType)) {
            throw new IllegalArgumentException("unsupported simulation tool");
        }
        requireId(targetId, "targetId");
    }

    private static void requireId(String value, String name) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid " + name);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static ObjectMapper mapper() {
        var factory = new com.fasterxml.jackson.core.JsonFactory();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        var mapper = new ObjectMapper(factory);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        return mapper;
    }
}
