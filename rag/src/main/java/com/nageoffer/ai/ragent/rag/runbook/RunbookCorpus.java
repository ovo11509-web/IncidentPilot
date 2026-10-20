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

package com.nageoffer.ai.ragent.rag.runbook;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

@Component public final class RunbookCorpus {
    public static final ObjectMapper JSON=new ObjectMapper();
    private final JsonNode manifest;private final List<Document> documents;
    public record Section(String sectionId,int lineStart,int lineEnd,String quote,String chunkDigest) {}
    public record Document(JsonNode metadata,String content,List<Section> sections) {
        public Document {metadata=metadata.deepCopy();sections=List.copyOf(sections);}
        @Override public JsonNode metadata(){return metadata.deepCopy();}
    }
    public RunbookCorpus() {
        RunbookCorpus loaded;
        try {loaded=new RunbookCorpus(load("manifest-v1.json"),RunbookCorpus::loadText);}
        catch(RuntimeException invalid){loaded=null;}
        // A broken optional diagnosis corpus must not prevent Incident/approval startup.
        this.manifest=loaded==null?JSON.createObjectNode().put("corpusVersion","UNAVAILABLE").put("corpusDigest","0".repeat(64)):loaded.manifest;
        this.documents=loaded==null?List.of():loaded.documents;
    }
    public RunbookCorpus(JsonNode manifest,java.util.function.Function<String,String> source) {
        this.manifest=manifest.deepCopy();var body=manifest.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)body).remove("corpusDigest");
        if(!manifest.path("corpusDigest").asText().equals(hash(canonical(body))))throw new IllegalArgumentException("invalid corpus digest");
        if(manifest.path("schemaVersion").asInt()!=1 || !manifest.path("runbooks").isArray())throw new IllegalArgumentException("invalid corpus");
        var parsed=new ArrayList<Document>();var identities=new HashSet<String>();
        for(var m:manifest.path("runbooks")) {
            for(String field:List.of("runbookId","version","currentVersion","title","owner","author","sourceType","sourcePath","reviewedAt","validUntil","documentDigest"))
                if(!m.path(field).isTextual() || m.path(field).asText().isBlank())throw new IllegalArgumentException("missing runbook metadata");
            if(!m.path("runbookId").asText().matches("[A-Z0-9-]{1,64}") || !"INCIDENTPILOT_SYNTHETIC".equals(m.path("sourceType").asText()))throw new IllegalArgumentException("invalid runbook identity");
            for(String field:List.of("services","environments","resourceTypes","symptomTerms","sections"))
                if(!m.path(field).isArray() || m.path(field).isEmpty())throw new IllegalArgumentException("missing runbook scope or sections");
            if(!OffsetDateTime.parse(m.path("reviewedAt").asText()).isBefore(OffsetDateTime.parse(m.path("validUntil").asText())))throw new IllegalArgumentException("invalid validity interval");
            if(!identities.add(m.path("runbookId").asText()+":"+m.path("version").asText()))throw new IllegalArgumentException("duplicate runbook version");
            if(!Set.of("PUBLISHED","WITHDRAWN").contains(m.path("status").asText()))throw new IllegalArgumentException("invalid status");
            String content=normalize(source.apply(safePath(m.path("sourcePath").asText())));
            if(!hash(content).equals(m.path("documentDigest").asText()))throw new IllegalArgumentException("invalid document digest");
            var lines=content.split("\n",-1);var sections=new ArrayList<Section>();var ids=new HashSet<String>();
            for(var s:m.path("sections")) {
                String id=s.path("sectionId").asText();int start=s.path("lineStart").asInt(),end=s.path("lineEnd").asInt();
                if(!ids.add(id) || start<1 || end<start || end>=lines.length || !lines[start-1].equals("## "+id))throw new IllegalArgumentException("invalid section position");
                String quote=String.join("\n",Arrays.copyOfRange(lines,start-1,end))+"\n";
                if(!hash(quote).equals(s.path("chunkDigest").asText()))throw new IllegalArgumentException("invalid chunk digest");
                sections.add(new Section(id,start,end,quote,hash(quote)));
            }
            parsed.add(new Document(m,content,sections));
        }
        this.documents=List.copyOf(parsed);
    }
    public List<Document> documents(){return documents;}
    public String version(){return manifest.path("corpusVersion").asText();}
    public String digest(){return manifest.path("corpusDigest").asText();}
    public boolean available(){return !documents.isEmpty();}
    public boolean applicable(Document doc,RunbookEvidence.Scope scope,OffsetDateTime now) {
        var m=doc.metadata();return "PUBLISHED".equals(m.path("status").asText())
            && m.path("version").asText().equals(m.path("currentVersion").asText())
            && !now.isBefore(OffsetDateTime.parse(m.path("reviewedAt").asText()))
            && now.isBefore(OffsetDateTime.parse(m.path("validUntil").asText()))
            && contains(m.path("services"),scope.service()) && contains(m.path("environments"),scope.environment())
            && contains(m.path("resourceTypes"),scope.resourceType());
    }
    private static boolean contains(JsonNode array,String value){for(var n:array)if(n.asText().equals(value))return true;return false;}
    public static String safePath(String path) {
        if(path==null || !path.matches("[A-Za-z0-9._/-]+") || path.startsWith("/") || path.contains(".."))throw new IllegalArgumentException("invalid corpus path");return path;
    }
    public static String normalize(String content){return content.replaceFirst("^\\uFEFF","").replace("\r\n","\n").replace('\r','\n').replaceAll("\n*$","")+"\n";}
    public static String hash(String content) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}
    }
    public static String canonical(JsonNode node) {
        try{return JSON.writeValueAsString(sorted(node));}catch(Exception e){throw new IllegalArgumentException("invalid corpus");}
    }
    private static JsonNode sorted(JsonNode n) {
        if(n.isObject()){var out=JSON.createObjectNode();var keys=new TreeSet<String>();n.fieldNames().forEachRemaining(keys::add);for(var k:keys)out.set(k,sorted(n.get(k)));return out;}
        if(n.isArray()){var out=JSON.createArrayNode();n.forEach(v->out.add(sorted(v)));return out;}return n;
    }
    private static JsonNode load(String path){try{return JSON.readTree(loadText(path));}catch(Exception e){throw new IllegalStateException("invalid packaged runbooks");}}
    private static String loadText(String path){try(var stream=RunbookCorpus.class.getResourceAsStream("/runbooks/"+safePath(path))){
        if(stream==null)throw new IllegalArgumentException("missing runbook");return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
    }catch(java.io.IOException e){throw new IllegalStateException("runbook unavailable");}}
}
