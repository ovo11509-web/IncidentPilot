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

package com.nageoffer.ai.ragent.incident.diagnosis.contract;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class DiagnosisContracts {
    public static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    static { JSON.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION); }
    private DiagnosisContracts() {}
    public static void staff() { StpUtil.checkLogin(); StpUtil.checkRole("admin"); }
    public static String actor() { staff(); return StpUtil.getLoginIdAsString(); }
    public static void id(String id) { if (id==null || !id.matches("[0-9]{1,20}")) throw new Failure(400,"INVALID_INPUT"); }
    public static String safeId(String id) {
        if (id==null || !id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw new Failure(400,"INVALID_INPUT");
        return id;
    }
    public static JsonNode parse(String text) {
        try { return JSON.readTree(text); } catch (Exception e) { throw new Failure(400,"INVALID_INPUT"); }
    }
    public static ObjectNode body(String raw, Set<String> allowed, Set<String> required) {
        if (raw==null || raw.getBytes(StandardCharsets.UTF_8).length>8192) throw new Failure(413,"INPUT_TOO_LARGE");
        var node=parse(raw);
        if (node==null || !node.isObject()) throw new Failure(400,"INVALID_INPUT");
        node.fieldNames().forEachRemaining(k->{ if (!allowed.contains(k)) throw new Failure(400,"INVALID_INPUT"); });
        for (String key: required) if (!node.hasNonNull(key)) throw new Failure(400,"INVALID_INPUT");
        return (ObjectNode)node;
    }
    public static String text(JsonNode body,String key) {
        if (!body.path(key).isTextual()) throw new Failure(400,"INVALID_INPUT"); return body.path(key).asText();
    }
    public static long version(JsonNode body,String key) {
        var v=body.path(key); if (!v.isIntegralNumber() || !v.canConvertToLong() || v.longValue()<0) throw new Failure(400,"INVALID_INPUT");
        return v.longValue();
    }
    public static String canonical(JsonNode node) {
        try { return JSON.writeValueAsString(sort(node)); } catch(Exception e) { throw new Failure(400,"INVALID_INPUT"); }
    }
    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            var out=JSON.createObjectNode(); var keys=new TreeSet<String>(); node.fieldNames().forEachRemaining(keys::add);
            for(String key:keys) out.set(key,sort(node.get(key))); return out;
        }
        if(node.isArray()) { var out=JSON.createArrayNode(); node.forEach(v->out.add(sort(v))); return out; }
        return node;
    }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static String digest(JsonNode node) { return hash(canonical(node)); }
    /** Boot 4 uses Jackson 3 at HTTP boundaries; never expose a Jackson 2 tree as a POJO. */
    public static Object wire(JsonNode node){return JSON.convertValue(node,Object.class);}
    public static ObjectNode failureResult(String status,String code) {
        var result=JSON.createObjectNode().put("contractVersion","runbook-evidence-v1").put("status",status).put("reasonCode",code);
        result.putArray("claims");result.putArray("recommendations");result.putArray("evidence");result.putArray("documentSnapshots");
        return result;
    }
    public record Begin(String diagnosisId, JsonNode snapshot, String ownerToken, boolean repeated, String requestState) {
        @Override public String toString(){return "Begin[diagnosisId="+diagnosisId+", requestState="+requestState+", ownerToken=REDACTED]";}
    }
    public static final class Failure extends RuntimeException {
        private final int status;private final String code;
        public Failure(int status,String code) { super(code);this.status=status;this.code=code; }
        public int status(){return status;} public String code(){return code;}
    }
}
