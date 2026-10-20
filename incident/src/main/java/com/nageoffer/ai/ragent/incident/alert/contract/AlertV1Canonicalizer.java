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

package com.nageoffer.ai.ragent.incident.alert.contract;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Strict v1 wire decoding and deterministic business-payload canonical form. */
@Component
public final class AlertV1Canonicalizer {
    private static final Set<String> FIELDS = Set.of("externalAlertId", "eventType", "occurredAt", "ruleId",
            "title", "severity", "environment", "service", "resource", "summary", "labels");
    private static final Pattern OPAQUE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/@-]*");
    private static final Pattern COMPONENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");
    private static final Pattern ENVIRONMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final Pattern LABEL_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]*");
    private final ObjectMapper mapper;

    public AlertV1Canonicalizer(IncidentProperties properties) {
        var factory = new com.fasterxml.jackson.core.JsonFactory();
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        factory.setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(properties.getIngress().getMaxJsonDepth()).build());
        mapper = new ObjectMapper(factory);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public JsonNode parseStructure(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode root = mapper.readTree(text);
            if (root == null || !root.isObject() || containsNull(root)) {
                throw new InvalidJsonException();
            }
            return root;
        } catch (CharacterCodingException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new InvalidJsonException();
        }
    }

    public CanonicalAlert canonicalize(JsonNode root) {
        try {
            root.fieldNames().forEachRemaining(name -> {
                if (!FIELDS.contains(name)) throw new InvalidContractException();
            });
            String externalId = field(root, "externalAlertId", 128, OPAQUE, false);
            if (!"FIRING".equals(field(root, "eventType", 16, null, false))) throw new InvalidContractException();
            String occurredText = field(root, "occurredAt", 64, null, false);
            OffsetDateTime occurredAt = OffsetDateTime.parse(occurredText);
            String rule = field(root, "ruleId", 128, COMPONENT, false);
            String title = field(root, "title", 256, null, false);
            String severity = field(root, "severity", 16, null, false);
            if (!Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(severity)) throw new InvalidContractException();
            String environment = field(root, "environment", 64, ENVIRONMENT, false);
            String service = field(root, "service", 128, COMPONENT, false);
            JsonNode resource = root.get("resource");
            if (resource == null || !resource.isObject() || resource.size() != 2
                    || !resource.has("type") || !resource.has("id")) throw new InvalidContractException();
            String resourceType = field(resource, "type", 64, COMPONENT, false);
            String resourceId = field(resource, "id", 256, OPAQUE, false);
            String summary = root.has("summary") ? field(root, "summary", 2048, null, false) : null;
            JsonNode labelNode = root.get("labels");
            if (labelNode != null && (!labelNode.isObject() || labelNode.size() > 32)) throw new InvalidContractException();
            Map<String, String> labels = new TreeMap<>();
            if (labelNode != null) {
                labelNode.fields().forEachRemaining(entry -> {
                    String key = normalize(entry.getKey(), 64, LABEL_KEY, false);
                    String value = normalizeNode(entry.getValue(), 256, null, true);
                    if (labels.putIfAbsent(key, value) != null) throw new InvalidContractException();
                });
            }
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("externalAlertId", externalId);
            canonical.put("eventType", "FIRING");
            canonical.put("occurredAt", occurredText);
            canonical.put("ruleId", rule);
            canonical.put("title", title);
            canonical.put("severity", severity);
            canonical.put("environment", environment);
            canonical.put("service", service);
            Map<String, String> normalizedResource = new LinkedHashMap<>();
            normalizedResource.put("type", resourceType);
            normalizedResource.put("id", resourceId);
            canonical.put("resource", normalizedResource);
            if (summary != null) canonical.put("summary", summary);
            canonical.put("labels", labels);
            String payload = mapper.writeValueAsString(canonical);
            String digest = hex(MessageDigest.getInstance("SHA-256")
                    .digest(("alert-payload-v1:" + (summary == null ? "missing:" : "present:") + payload)
                            .getBytes(StandardCharsets.UTF_8)));
            return new CanonicalAlert(externalId, occurredAt, rule, title, severity, environment,
                    service, resourceType, resourceId, summary, labels, payload, digest);
        } catch (InvalidContractException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidContractException();
        }
    }

    private static String field(JsonNode object, String name, int max, Pattern pattern, boolean emptyAllowed) {
        return normalizeNode(object.get(name), max, pattern, emptyAllowed);
    }

    private static String normalizeNode(JsonNode node, int max, Pattern pattern, boolean emptyAllowed) {
        if (node == null || !node.isTextual()) throw new InvalidContractException();
        return normalize(node.textValue(), max, pattern, emptyAllowed);
    }

    private static String normalize(String raw, int max, Pattern pattern, boolean emptyAllowed) {
        if (raw.codePointCount(0, raw.length()) > max) throw new InvalidContractException();
        String nfc = Normalizer.normalize(raw, Normalizer.Form.NFC);
        int start = 0, end = nfc.length();
        while (start < end && whitespace(nfc.codePointAt(start))) start += Character.charCount(nfc.codePointAt(start));
        while (start < end && whitespace(nfc.codePointBefore(end))) end -= Character.charCount(nfc.codePointBefore(end));
        String value = nfc.substring(start, end);
        if ((!emptyAllowed && value.isEmpty()) || value.codePointCount(0, value.length()) > max
                || (pattern != null && !pattern.matcher(value).matches())) throw new InvalidContractException();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || (Character.isSurrogate(c)
                    && (i + 1 >= value.length() || !Character.isSurrogatePair(c, value.charAt(i + 1)))))
                throw new InvalidContractException();
            if (Character.isHighSurrogate(c)) i++;
        }
        return value;
    }

    private static boolean containsNull(JsonNode node) {
        if (node.isNull()) return true;
        if (node.isContainerNode()) {
            for (JsonNode child : node) if (containsNull(child)) return true;
        }
        return false;
    }

    private static boolean whitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    public record CanonicalAlert(String externalAlertId, OffsetDateTime occurredAt, String ruleId,
            String title, String severity, String environment, String service, String resourceType,
            String resourceId, String summary, Map<String, String> labels, String payloadJson, String payloadDigest) {}

    public static final class InvalidJsonException extends RuntimeException {}
    public static final class InvalidContractException extends RuntimeException {}
}
