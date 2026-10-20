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

package com.nageoffer.ai.ragent.mcp.simulation;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.*;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.common.AttributeKey;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.slf4j.MDC;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProtectedSimulationTraceTest {
    @Test void cancellationDoesNotEndSpanUntilActualTaskExits() throws Exception {
        var spans=new CopyOnWriteArrayList<SpanData>();
        var entered=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
        try (var provider=SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter(spans))).build()) {
            McpObservation.configure(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            var timeout=new SimulationTimeoutEvaluator(java.time.Duration.ofSeconds(2));
            try (var server=McpObservation.start("incident.http",SpanKind.SERVER)) {
                server.id("executionId","native-timeout");
                var result=timeout.evaluate(() -> {
                    entered.countDown();
                    while(release.getCount()>0) try { release.await(); } catch(InterruptedException ignored) { }
                    return new SimulationToolExecutor.Application("APPLIED","PAUSED","READY");
                },20,()->false);
                assertThat(entered.getCount()).isZero();
                assertThat(result.resultStatus()).isEqualTo("UNKNOWN");
                assertThat(spans).noneMatch(s->s.getName().equals("incident.mcp.execute"));
                release.countDown();
                long due=System.nanoTime()+java.time.Duration.ofSeconds(3).toNanos();
                while(spans.stream().noneMatch(s->s.getName().equals("incident.mcp.execute")) && System.nanoTime()<due) Thread.sleep(10);
                assertThat(spans).anyMatch(s->s.getName().equals("incident.mcp.execute"));
            }
        } finally { release.countDown(); McpObservation.configure(null); MDC.clear(); }
    }

    @Test void concurrentAndRepeatedNativeTasksNeverMixExecutionIdentity() throws Exception {
        var spans=new CopyOnWriteArrayList<SpanData>();
        var callers=java.util.concurrent.Executors.newFixedThreadPool(2);
        try (var provider=SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter(spans))).build()) {
            McpObservation.configure(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            for(int round=0;round<4;round++) {
                var futures=new ArrayList<java.util.concurrent.Future<?>>();
                for(int i=0;i<2;i++) {
                    String id="execution-"+round+"-"+i;
                    futures.add(callers.submit(()-> {
                        MDC.put("outer","retained");
                        try(var scope=McpObservation.start("incident.http",SpanKind.SERVER)) {
                            scope.id("executionId",id);
                            var result=new SimulationTimeoutEvaluator().evaluate(()-> {
                                assertThat(MDC.get("executionId")).isEqualTo(id);
                                assertThat(MDC.get("outer")).isNull();
                                return new SimulationToolExecutor.Application("APPLIED","PAUSED","READY");
                            });
                            assertThat(result.resultStatus()).isEqualTo("SUCCESS");
                        }
                        assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("outer","retained")); MDC.clear();
                    }));
                }
                for(var future:futures) future.get();
            }
            assertThat(spans.stream().filter(s->s.getName().equals("incident.mcp.execute"))).hasSize(8);
            for(var child:spans.stream().filter(s->s.getName().equals("incident.mcp.execute")).toList()) {
                var parent=spans.stream().filter(s->s.getSpanId().equals(child.getParentSpanId())).findFirst().orElseThrow();
                assertThat(child.getAttributes().get(AttributeKey.stringKey("ragent.execution_id")))
                        .isEqualTo(parent.getAttributes().get(AttributeKey.stringKey("ragent.execution_id")));
            }
        } finally { callers.shutdownNow(); McpObservation.configure(null); MDC.clear(); }
    }
    @Test void onlyGrantedExecutionHasTrustedIdentityAndNativeThreadParent() {
        var spans = new CopyOnWriteArrayList<SpanData>();
        try (var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter(spans))).build()) {
            McpObservation.configure(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            var executor = mock(SimulationToolExecutor.class); var callbacks = mock(IncidentCallbackClient.class);
            when(executor.isRegisteredTarget("demo")).thenReturn(true);
            when(executor.apply(anyString(),anyString(),anyString(),anyLong()))
                    .thenReturn(new SimulationToolExecutor.Application("APPLIED","PAUSED","READY"));
            var tool = new ProtectedSimulationTool(executor,callbacks);
            var meta = Map.<String,Object>of("actionId","200","incidentId","100","executionId","300",
                    "executionKey","not-a-log-key","requestId","dispatch-300","ticket","never-log-ticket","bindingVersion","action-binding-v1");
            var request = new CallToolRequest("simulation.set-state",Map.of("targetId","demo","desiredState","READY"),meta);
            when(callbacks.consume(anyMap())).thenReturn(new IncidentCallbackClient.ConsumeGrant("DUPLICATE",-1));
            try (var server = McpObservation.start("incident.http",SpanKind.SERVER)) {
                assertThat(tool.invoke(request).isError()).isTrue();
            }
            assertThat(spans).noneMatch(s -> s.getName().equals("incident.mcp.execute"));
            assertThat(spans.get(0).getAttributes().get(AttributeKey.stringKey("ragent.incident_id"))).isNull();
            spans.clear();
            when(callbacks.consume(anyMap())).thenReturn(new IncidentCallbackClient.ConsumeGrant("GRANTED",120000));
            String parent;
            try (var server = McpObservation.start("incident.http",SpanKind.SERVER)) {
                parent=server.fields().get("spanId");
                assertThat(tool.invoke(request).isError()).isFalse();
                assertThat(MDC.get("executionId")).isNull();
            }
            var execution = spans.stream().filter(s -> s.getName().equals("incident.mcp.execute")).findFirst().orElseThrow();
            assertThat(execution.getParentSpanId()).isEqualTo(parent);
            assertThat(execution.getAttributes().get(AttributeKey.stringKey("ragent.execution_id"))).isEqualTo("300");
            assertThat(spans.toString()).doesNotContain("never-log-ticket","not-a-log-key","desiredState");
            verify(executor,times(1)).apply(anyString(),anyString(),anyString(),anyLong());
        } finally { McpObservation.configure(null); MDC.clear(); }
    }

    @Test void invalidOrUnauthenticatedPropagationStartsANewRoot() {
        for (String value : List.of("invalid","00-"+"0".repeat(32)+"-"+"0".repeat(16)+"-01","x".repeat(257))) {
            var request = new MockHttpServletRequest(); request.addHeader("traceparent",value);
            assertThat(io.opentelemetry.api.trace.Span.fromContext(McpObservation.extract(request,true)).getSpanContext().isValid()).isFalse();
        }
        var request = new MockHttpServletRequest(); request.addHeader("traceparent","00-"+"a".repeat(32)+"-"+"b".repeat(16)+"-01");
        assertThat(io.opentelemetry.api.trace.Span.fromContext(McpObservation.extract(request,false)).getSpanContext().isValid()).isFalse();
        request.addHeader("traceparent","00-"+"c".repeat(32)+"-"+"d".repeat(16)+"-01");
        assertThat(io.opentelemetry.api.trace.Span.fromContext(McpObservation.extract(request,true)).getSpanContext().isValid()).isFalse();
    }

    static SpanExporter exporter(List<SpanData> spans) {
        return new SpanExporter() {
            public CompletableResultCode export(Collection<SpanData> data) { spans.addAll(data); return CompletableResultCode.ofSuccess(); }
            public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
    }
}
