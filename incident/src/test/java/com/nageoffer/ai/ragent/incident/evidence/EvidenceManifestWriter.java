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

package com.nageoffer.ai.ragent.incident.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Writes allowlisted, generated evidence under the Maven target directory. */
public final class EvidenceManifestWriter {
    private final ObjectMapper json = new ObjectMapper();
    private final Path directory;

    public EvidenceManifestWriter() throws IOException {
        directory = Files.createDirectories(Path.of("target", "incident-evidence",
                Instant.now().toString().replace(':', '-') + "-" + UUID.randomUUID()));
    }

    public Path directory() { return directory; }

    public void write(String name, Object data) throws IOException {
        if (!java.util.Set.of("manifest.json", "scenario-results.json", "persistence-counts.json",
                "event-captures.jsonl", "content-scan.json").contains(name))
            throw new IllegalArgumentException("Unknown evidence artifact");
        json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve(name).toFile(), data);
    }

    public void eventCapture(Map<String, ?> event) throws IOException {
        Files.writeString(directory.resolve("event-captures.jsonl"), json.writeValueAsString(event) + "\n",
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }

    /** Optional lab output only; default tests keep their original behavior. */
    public static void labProof(String testName, Map<String, ?> facts) throws IOException {
        String root = System.getProperty("lab.proofRoot");
        if (root == null) return;
        String runId = System.getProperty("lab.runId", "");
        String scenario = System.getProperty("lab.scenarioId", "");
        UUID.fromString(runId);
        if (!scenario.matches("[A-Z-]+") || !testName.matches("[A-Za-z]+"))
            throw new IllegalArgumentException("invalid lab identity");
        Path output = Path.of(root).toAbsolutePath().normalize().resolve("proof");
        Path allowed = Path.of("..", "bootstrap", "target",
                Boolean.getBoolean("delivery.gates") ? "delivery-gates" : "fault-lab")
                .toAbsolutePath().normalize();
        if (!output.startsWith(allowed)) throw new IllegalArgumentException("lab output outside target");
        Files.createDirectories(output);
        var mapper = new ObjectMapper();
        String content = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(facts);
        if (content.contains("SECRET_PROBE")) throw new IllegalArgumentException("unsafe lab evidence");
        Files.writeString(output.resolve(testName + ".json"), content);
        java.util.List<String> tests;
        try (var files = Files.list(output)) {
            tests = files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")
                    && !n.equals("proof-origin.json")).map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("proof-origin.json").toFile(),
                Map.of("runId", runId, "scenarioId", scenario, "sourceDigest", System.getProperty("lab.sourceDigest"), "tests", tests,
                        "javaRuntime", System.getProperty("java.runtime.version"), "compileRelease", 17));
    }
}
