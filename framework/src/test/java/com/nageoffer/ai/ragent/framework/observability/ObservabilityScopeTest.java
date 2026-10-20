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

package com.nageoffer.ai.ragent.framework.observability;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityScopeTest {
    @Test void identityOutsideObservationCannotLeakIntoTheNextOperation() {
        MDC.clear();
        ObservationScope.identify("diagnosisId", "old-diagnosis");
        assertThat(MDC.get("diagnosisId")).isNull();
        try (var scope = ObservationScope.start("incident.diagnosis", SpanKind.INTERNAL)) {
            ObservationScope.identify("diagnosisId", "current-diagnosis");
            assertThat(MDC.get("diagnosisId")).isEqualTo("current-diagnosis");
        }
        assertThat(MDC.get("diagnosisId")).isNull();
        try (var next = ObservationScope.start("incident.alert.process", SpanKind.INTERNAL)) {
            assertThat(next.fields()).doesNotContainKey("diagnosisId");
        }
    }
    @Test void disabledObservationDoesNotPropagateAnUnrelatedAgentTrace() {
        var trace=io.opentelemetry.api.trace.Span.wrap(io.opentelemetry.api.trace.SpanContext.create(
                "a".repeat(32),"b".repeat(16),io.opentelemetry.api.trace.TraceFlags.getSampled(),io.opentelemetry.api.trace.TraceState.getDefault()));
        var headers=new java.util.HashMap<String,String>();
        ObservationScope.inject(io.opentelemetry.context.Context.root().with(trace),headers::put);
        assertThat(headers).isEmpty();
        MDC.put("traceId", "foreign-agent-trace"); MDC.put("spanId", "foreign-agent-span");
        try(var scope=ObservationScope.start("incident.http",SpanKind.SERVER)) {
            assertThat(scope.fields()).doesNotContainKeys("traceId","spanId");
            assertThat(MDC.get("traceId")).isNull();
        }
        assertThat(MDC.get("traceId")).isEqualTo("foreign-agent-trace");
    }
    @AfterEach void cleanup() { ObservationScope.configure(null); MDC.clear(); }

    @Test void restoresOuterMdcAndRejectsUnlistedOrUnboundedValues() {
        MDC.put("outer", "keep");
        try (var scope = ObservationScope.start("incident.alert.receive", SpanKind.INTERNAL)) {
            scope.id("incidentId", "123").id("ticket", "secret")
                    .id("actionId", "x".repeat(129)).id("requestId", "bad\nvalue");
            assertThat(scope.fields()).containsEntry("incidentId", "123")
                    .doesNotContainKeys("ticket", "actionId", "requestId", "outer");
        }
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("outer", "keep"));
    }

    @Test void requestAttemptIsAssignedOnceAndIgnoresCallerHeader() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "caller-supplied");
        String assigned = ObservationScope.requestId(request);
        assertThat(ObservationScope.requestId(request)).isEqualTo(assigned).isNotEqualTo("caller-supplied");
    }

    @Test void childSharesTraceAndExplicitSnapshotRestoresReusedThread() throws Exception {
        try (var provider = SdkTracerProvider.builder().build()) {
            ObservationScope.configure(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            ObservationScope.Snapshot snapshot;
            String trace;
            try (var outer = ObservationScope.start("incident.action.dispatch", SpanKind.INTERNAL)) {
                outer.id("executionId", "execution-1");
                snapshot = ObservationScope.capture();
                trace = outer.fields().get("traceId");
            }
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> {
                    MDC.put("outer", "thread");
                    try (var context = snapshot.makeCurrent();
                         var child = ObservationScope.start("incident.ticket.consume", SpanKind.INTERNAL)) {
                        assertThat(child.fields()).containsEntry("traceId", trace)
                                .containsEntry("executionId", "execution-1");
                    }
                    assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("outer", "thread"));
                }).get();
                executor.submit(() -> assertThat(MDC.get("executionId")).isNull()).get();
            } finally { executor.shutdownNow(); }
        }
    }
}
