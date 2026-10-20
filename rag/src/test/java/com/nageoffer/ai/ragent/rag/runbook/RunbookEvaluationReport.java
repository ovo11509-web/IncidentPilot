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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.*;
import java.time.Instant;
final class RunbookEvaluationReport {
    static void write(JsonNode report, JsonNode dataset, Instant startedAt, String corpusVersion) throws Exception {
        ObjectNode root=(ObjectNode)report;
        String runId=java.util.UUID.randomUUID().toString();
        Instant finishedAt=Instant.now();
        root.put("runId",runId).put("jvmVersion",System.getProperty("java.version")).put("generatedAt",finishedAt.toString());
        var manifest=RunbookCorpus.JSON.createObjectNode().put("runId",runId).put("runType","ACTUAL_LEXICAL_RETRIEVAL")
            .put("actualCommand",System.getenv().getOrDefault("RAG_EVAL_COMMAND","TBD: command not supplied; see invocation log"))
            .put("startedAt",startedAt.toString()).put("finishedAt",finishedAt.toString()).put("javaVersion",System.getProperty("java.version"))
            .put("datasetVersion",dataset.path("datasetVersion").asText()).put("datasetDigest",root.path("datasetDigest").asText())
            .put("corpusVersion",corpusVersion).put("corpusDigest",root.path("corpusDigest").asText()).put("fixtureClock",dataset.path("fixtureClock").asText())
            .put("retrieverVersion","lexical-v1").put("retrievalConfigVersion","lexical-v1-top20-expanded").put("generatorVersion","NONE").put("ruleVersion","NONE")
            .put("promptVersion","NONE").put("snapshotVersion","NONE_QUERY_FACTS_ONLY").put("testEntryPoint","RunbookOfflineEvaluationTest")
            .put("isolation","CLASSPATH_FIXTURE_NO_EXTERNAL_SERVICES").put("modelCalls",0).put("writeCalls",0)
            .put("humanLabelReview","PENDING").put("labelOrigin",dataset.path("labelOrigin").asText());
        manifest.setAll(sourceIdentity());
        var config=manifest.putObject("configuration").put("topK",3).put("maxChunks",20).put("maxEvidenceBytes",262144)
            .put("service","incident-demo").put("environment","demo")
            .put("resourceType","sim_service").put("selectionConfiguration","NOT_USED_OFFLINE_NO_ACTION_SELECTION");
        manifest.put("configurationDigest",RunbookCorpus.hash(RunbookCorpus.canonical(config)));
        root.set("manifest",manifest);
        Files.createDirectories(Path.of("target/runbook-evaluation"));write(Path.of("target/runbook-evaluation/report-"+(dataset.path("datasetVersion").asText().endsWith("v2")?"v2":"v1")+".json"),root);
        Path directory=Path.of("target/runbook-eval",runId);Files.createDirectories(directory);
        var summary=root.deepCopy();summary.remove("cases");write(directory.resolve("summary.json"),summary);
        write(directory.resolve("cases.json"),root.path("cases"));write(directory.resolve("manifest.json"),manifest);
    }
    static ObjectNode sourceIdentity() throws Exception {
        var source=RunbookCorpus.JSON.createObjectNode();
        if (Boolean.getBoolean("delivery.gates")) {
            String runId=System.getProperty("lab.runId","");
            String scenarioId=System.getProperty("lab.scenarioId","");
            String sourceDigest=System.getProperty("lab.sourceDigest","");
            if (!runId.matches("[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}")
                    || !java.util.UUID.fromString(runId).toString().equals(runId)
                    || !java.util.Set.of("DELIVERY-SMOKE","DELIVERY-BACKEND").contains(scenarioId)
                    || !sourceDigest.matches("[A-F0-9]{64}"))
                throw new IllegalStateException("Invalid delivery evidence identity");
            return source.put("sourceKind","DELIVERY_GATE").put("gateRunId",runId)
                    .put("scenarioId",scenarioId).put("sourceDigest",sourceDigest)
                    .putNull("gitHead").putNull("gitDirty");
        }
        return source.put("sourceKind","GIT").put("gitHead",git("rev-parse","HEAD"))
                .put("gitDirty",!git("status","--porcelain").isEmpty());
    }
    private static String git(String... args) throws Exception {
        var command=new java.util.ArrayList<String>();command.add("git");command.addAll(java.util.List.of(args));
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
        if(process.waitFor()!=0)throw new IllegalStateException("Read-only Git metadata unavailable");return output;
    }
    private static void write(Path path,JsonNode node) throws Exception { Files.writeString(path,RunbookCorpus.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node)); }
}
