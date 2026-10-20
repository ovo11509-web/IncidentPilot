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

package com.nageoffer.ai.ragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real application JVMs, real local HTTP MCP SDK, temporary PostgreSQL and deterministic runbooks. */
class IncidentObservabilityIsolatedTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Test void actualInvalidAndStaleDiagnosisRequestsCannotExecuteAndKeepAttemptIdentity() throws Exception {
        Path proof=BootstrapIsolatedStartupTest.runObserved("diagnosis-safety");var spans=read(proof);
        assertThat(JSON.readTree(proof.resolve("summary.json").toFile()).path("simulationApplications").asInt()).isZero();
        assertThat(spans).noneMatch(s->s.path("name").asText().equals("incident.mcp.execute"));
        writeProof(proof,spans,"Q01-Q02-negative",0);
    }
    @Test void twoAlertsDiagnosisRejectionAndApprovalHaveActualCrossProcessAncestry() throws Exception {
        Path proof=BootstrapIsolatedStartupTest.runObserved("diagnosis-golden");
        var spans=read(proof);
        var summary=JSON.readTree(proof.resolve("summary.json").toFile());
        assertThat(summary.path("simulationApplications").asInt()).isEqualTo(1);
        assertThat(spans).extracting(s->s.path("name").asText()).contains("incident.alert.receive","incident.alert.process",
                "incident.diagnosis","incident.runbook.retrieve","incident.diagnosis.generate","incident.diagnosis.validate",
                "incident.action.decide","incident.mcp.dispatch","incident.ticket.consume","incident.mcp.execute","incident.receipt.accept");
        assertContinuousCalls(spans);
        var execution=spans.stream().filter(s->s.path("name").asText().equals("incident.mcp.execute")).toList();
        assertThat(execution).hasSize(1);
        String incident=execution.get(0).path("attributes").path("ragent.incident_id").asText();
        assertThat(incident).isNotBlank();
        var decisions=spans.stream().filter(s->s.path("name").asText().equals("incident.action.decide")).toList();
        assertThat(decisions).extracting(s->s.path("attributes").path("ragent.outcome").asText()).contains("APPROVAL_REJECTED","APPROVED");
        assertThat(decisions).allMatch(s->incident.equals(s.path("attributes").path("ragent.incident_id").asText()));
        var bridges=spans.stream().filter(s->s.path("name").asText().equals("incident.alert.process")
                && incident.equals(s.path("attributes").path("ragent.incident_id").asText())).toList();
        assertThat(bridges).hasSizeGreaterThanOrEqualTo(2);
        assertThat(bridges.stream().map(s->s.path("attributes").path("ragent.correlation_id").asText()).distinct()).hasSize(2);
        writeProof(proof,spans,"Q01-Q05",1);
    }
    @Test void actualWorkerCrashRecoversStableExecutionInNewTrace() throws Exception {
        Path proof=BootstrapIsolatedStartupTest.runObserved("worker-recovery"); var spans=read(proof);
        var execute=spans.stream().filter(s->s.path("name").asText().equals("incident.mcp.execute")).findFirst().orElseThrow();
        String id=execute.path("attributes").path("ragent.execution_id").asText();
        var dispatch=spans.stream().filter(s->s.path("name").asText().equals("incident.mcp.dispatch")
                && id.equals(s.path("attributes").path("ragent.execution_id").asText())).toList();
        assertThat(dispatch).isNotEmpty();
        assertThat(spans.stream().filter(s->s.path("service").asText().equals("incident-main"))
                .map(s->s.path("instance").asText()).distinct()).hasSize(2);
        var summary=JSON.readTree(proof.resolve("summary.json").toFile());
        assertThat(summary.path("simulationApplications").asInt()).isEqualTo(1);
        assertThat(spans).anyMatch(s->s.path("name").asText().equals("incident.action.recover"));
        var phases=spans.stream().filter(s->Set.of("incident.action.dispatch","incident.action.recover").contains(s.path("name").asText())
                && id.equals(s.path("attributes").path("ragent.execution_id").asText())).toList();
        assertThat(phases.stream().map(s->s.path("traceId").asText()).distinct()).hasSizeGreaterThanOrEqualTo(2);
        String dispatchRequest=execute.path("attributes").path("ragent.dispatch_request_id").asText();
        assertThat(phases).allMatch(s->dispatchRequest.equals(s.path("attributes").path("ragent.dispatch_request_id").asText()));
        writeProof(proof,spans,"Q06",1);
    }
    @Test void lostReceiptChannelsRemainUnknownUntilTrustedLateReceipt() throws Exception {
        Path proof=BootstrapIsolatedStartupTest.runObserved("receipt-channels"); var spans=read(proof);
        var summary=JSON.readTree(proof.resolve("summary.json").toFile());
        assertThat(summary.path("simulationApplications").asInt()).isEqualTo(3);
        assertThat(spans.stream().filter(s->s.path("name").asText().equals("incident.mcp.execute"))).hasSize(3);
        assertThat(spans).anyMatch(s->s.path("name").asText().equals("incident.action.sweep"));
        assertThat(spans).anyMatch(s->s.path("name").asText().equals("incident.receipt.accept"));
        writeProof(proof,spans,"Q07",3);
    }
    static List<JsonNode> read(Path proof) throws Exception {
        var result=new ArrayList<JsonNode>();
        Set<String> keys=Set.of("service","operation","phase","outcome","reason_code","request_id","trace_id","span_id",
                "incident_id","alert_id","correlation_id","diagnosis_id","action_id","approval_id","execution_id",
                "dispatch_request_id","source_request_id","receipt_id","event_id","attempt","processing_epoch");
        for(String file:List.of("main-spans.jsonl","mcp-spans.jsonl")) {
            for(String line:Files.readAllLines(proof.resolve(file))) {
                var span=JSON.readTree(line); result.add(span);
                assertThat(span.path("endNanos").asLong()).isGreaterThanOrEqualTo(span.path("startNanos").asLong());
                assertThat(span.path("traceId").asText()).matches("[a-f0-9]{32}");
                assertThat(span.path("attributes").size()).isLessThanOrEqualTo(24);
                span.path("attributes").fields().forEachRemaining(e->{
                    assertThat(e.getKey()).startsWith("ragent."); assertThat(keys).contains(e.getKey().substring(7));
                    assertThat(e.getValue().asText()).hasSizeLessThanOrEqualTo(128);
                });
            }
        }
        assertThat(result).isNotEmpty();
        var requests=JSON.readTree(proof.resolve("request-ids.json").toFile());
        String logs="";
        try(var files=Files.list(proof)) {
            for(var file:files.filter(p->p.getFileName().toString().endsWith("process.log")).toList()) logs+=Files.readString(file);
        }
        for(var request:requests) {
            String id=request.asText();
            assertThat(logs).contains("\"requestId\":\""+id+"\"");
            assertThat(result).anyMatch(s->s.path("kind").asText().equals("SERVER")
                    && id.equals(s.path("attributes").path("ragent.request_id").asText()));
        }
        return result;
    }
    static void assertContinuousCalls(List<JsonNode> spans) {
        var clients=spans.stream().filter(s->s.path("kind").asText().equals("CLIENT")).toList();
        for(var client:clients) {
            if(!Set.of("incident.mcp.dispatch","incident.callback.consume","incident.callback.receipt").contains(client.path("name").asText()))continue;
            var servers=spans.stream().filter(s->s.path("kind").asText().equals("SERVER")
                    && s.path("parentSpanId").asText().equals(client.path("spanId").asText())).toList();
            assertThat(servers).as("actual server parent of %s",client.path("name").asText()).isNotEmpty();
            assertThat(servers).allMatch(s->s.path("traceId").asText().equals(client.path("traceId").asText()));
        }
        var execution=spans.stream().filter(s->s.path("name").asText().equals("incident.mcp.execute")).findFirst().orElseThrow();
        var server=spans.stream().filter(s->s.path("spanId").asText().equals(execution.path("parentSpanId").asText())).findFirst().orElseThrow();
        assertThat(server.path("service").asText()).isEqualTo("incident-mcp");
        assertThat(server.path("kind").asText()).isEqualTo("SERVER");
        assertThat(server.path("traceId").asText()).isEqualTo(execution.path("traceId").asText());
    }
    static void writeProof(Path proof,List<JsonNode> spans,String scenarios,int applications) throws Exception {
        var manifest=JSON.createObjectNode().put("status","PASS").put("scenarios",scenarios).put("actualSpans",spans.size())
                .put("simulationApplications",applications).put("otlpBackendValidated",false).put("modelCalls",0);
        Files.writeString(proof.resolve("trace-proof.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
        if (System.getProperty("lab.runId") != null) {
            String method = switch (scenarios) {
                case "Q01-Q02-negative" -> "actualInvalidAndStaleDiagnosisRequestsCannotExecuteAndKeepAttemptIdentity";
                case "Q01-Q05" -> "twoAlertsDiagnosisRejectionAndApprovalHaveActualCrossProcessAncestry";
                case "Q06" -> "actualWorkerCrashRecoversStableExecutionInNewTrace";
                case "Q07" -> "lostReceiptChannelsRemainUnknownUntilTrustedLateReceipt";
                default -> throw new IllegalArgumentException("unknown lab proof");
            };
            JSON.writerWithDefaultPrettyPrinter().writeValue(proof.resolve("proof-origin.json").toFile(),
                    Map.of("runId", System.getProperty("lab.runId"), "scenarioId", System.getProperty("lab.scenarioId"),
                            "sourceDigest", System.getProperty("lab.sourceDigest"), "tests", List.of(method), "javaRuntime", System.getProperty("java.runtime.version"), "compileRelease", 17));
        }
        BootstrapIsolatedStartupTest.scanObservedProof(proof);
    }
}
