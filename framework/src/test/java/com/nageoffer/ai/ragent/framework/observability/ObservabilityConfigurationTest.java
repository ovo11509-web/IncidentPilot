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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityConfigurationTest {
    @Test void failedSlowExporterAndQueuePressureCannotChangeOperationCount() throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var received = new java.util.concurrent.CountDownLatch(1);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/traces", exchange -> {
            received.countDown(); exchange.getRequestBody().readAllBytes();
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.sendResponseHeaders(503,-1); exchange.close();
        }); server.start();
        try {
            new ApplicationContextRunner().withUserConfiguration(ObservabilityConfiguration.class)
                    .withPropertyValues("observability.enabled=true", "observability.endpoint=http://127.0.0.1:"
                            + server.getAddress().getPort()+"/v1/traces")
                    .run(ctx -> {
                        assertThat(ctx).hasNotFailed();
                        // Bypass application logging for queue pressure; exercise the actual configured BSP.
                        var tracer=ctx.getBean(OpenTelemetrySdk.class).getTracer("export-failure-test");
                        for(int i=0;i<5000;i++) { var span=tracer.spanBuilder("incident.alert.receive").startSpan(); calls.incrementAndGet(); span.end(); }
                        assertThat(calls.get()).isEqualTo(5000);
                        try { assertThat(received.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue(); }
                        catch (InterruptedException e) { throw new AssertionError(e); }
                    });
        } finally { server.stop(0); }
    }
    @AfterEach void reset() { GlobalOpenTelemetry.resetForTest(); ObservationScope.configure(null); }
    @Test void disabledDoesNotCreateOrTouchGlobalSdk() {
        new ApplicationContextRunner().withUserConfiguration(ObservabilityConfiguration.class)
                .run(ctx -> { assertThat(ctx).doesNotHaveBean(OpenTelemetrySdk.class);
                    assertThat(GlobalOpenTelemetry.isSet()).isFalse(); });
    }
    @Test void enabledWithoutExporterRecordsRealIdsAndCloses() {
        new ApplicationContextRunner().withUserConfiguration(ObservabilityConfiguration.class)
                .withPropertyValues("observability.enabled=true", "spring.application.name=test")
                .run(ctx -> { assertThat(ctx).hasSingleBean(OpenTelemetrySdk.class);
                    try (var scope = ObservationScope.start("incident.http", io.opentelemetry.api.trace.SpanKind.SERVER)) {
                        assertThat(scope.fields().get("traceId")).matches("[0-9a-f]{32}");
                    } });
    }
    @Test void invalidEndpointFailsStartup() {
        new ApplicationContextRunner().withUserConfiguration(ObservabilityConfiguration.class)
                .withPropertyValues("observability.enabled=true", "observability.endpoint=not-a-uri")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
