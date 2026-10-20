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
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RunbookCorpusTest {
    @Test void packagedVersionsHaveVerifiableSectionsAndSafePaths() {
        var corpus = new RunbookCorpus();
        assertEquals(5, corpus.documents().size());
        for (var doc : corpus.documents()) {
            assertEquals(doc.metadata().path("documentDigest").asText(), RunbookCorpus.hash(doc.content()));
            for (var section : doc.sections()) {
                assertEquals(section.chunkDigest(), RunbookCorpus.hash(section.quote()));
                assertTrue(section.lineStart() > 0 && section.lineEnd() >= section.lineStart());
            }
        }
        assertThrows(IllegalArgumentException.class, () -> RunbookCorpus.safePath("../secrets"));
        assertThrows(IllegalArgumentException.class, () -> RunbookCorpus.safePath("https://example.com"));
        assertThrows(IllegalArgumentException.class, () -> RunbookCorpus.safePath("C:/secrets"));
    }
    @Test void alteredDuplicateWithdrawnAndOldVersionsFailClosed() throws Exception {
        var manifest=RunbookCorpus.JSON.readTree(getClass().getResourceAsStream("/runbooks/manifest-v1.json"));
        var original=new RunbookCorpus();var sources=new java.util.HashMap<String,String>();for(var d:original.documents())sources.put(d.metadata().path("sourcePath").asText(),d.content());
        var duplicate=manifest.deepCopy();((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.path("runbooks")).add(duplicate.path("runbooks").get(0).deepCopy());rehash(duplicate);
        assertThrows(IllegalArgumentException.class,()->new RunbookCorpus(duplicate,sources::get));
        assertThrows(IllegalArgumentException.class,()->new RunbookCorpus(manifest,p->sources.get(p)+"tampered"));
        var withdrawn=manifest.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)withdrawn.path("runbooks").get(0)).put("status","WITHDRAWN");rehash(withdrawn);
        var changed=new RunbookCorpus(withdrawn,sources::get);var scope=new RunbookEvidence.Scope("incident-demo","demo","sim_service");
        assertFalse(changed.applicable(changed.documents().get(0),scope,java.time.OffsetDateTime.parse("2026-10-03T12:00:00Z")));
        var old=manifest.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)old.path("runbooks").get(0)).put("currentVersion","2.0.0");rehash(old);
        var versions=new RunbookCorpus(old,sources::get);assertFalse(versions.applicable(versions.documents().get(0),scope,java.time.OffsetDateTime.parse("2026-10-03T12:00:00Z")));
        // Stored immutable text remains independently verifiable after current metadata changes.
        assertEquals(original.documents().get(0).metadata().path("documentDigest").asText(),RunbookCorpus.hash(changed.documents().get(0).content()));
    }
    private static void rehash(com.fasterxml.jackson.databind.JsonNode node){var copy=node.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)copy).remove("corpusDigest");((com.fasterxml.jackson.databind.node.ObjectNode)node).put("corpusDigest",RunbookCorpus.hash(RunbookCorpus.canonical(copy)));}
}
