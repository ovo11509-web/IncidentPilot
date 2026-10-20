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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

@Configuration
@ConditionalOnProperty(prefix="observability", name="enabled", havingValue="true")
public class ObservabilityConfiguration {
    @Bean(destroyMethod="close")
    public OpenTelemetrySdk incidentOpenTelemetrySdk(
            @Value("${spring.application.name:ragent}") String name,
            @Value("${observability.endpoint:}") String endpoint,
            @Qualifier("agentObservabilityCustomizer") ObjectProvider<Consumer<SdkTracerProviderBuilder>> agent) {
        if (GlobalOpenTelemetry.isSet()) throw new IllegalStateException("OpenTelemetry SDK owner already registered");
        var builder = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn())
                .setResource(Resource.getDefault().merge(Resource.create(Attributes.builder()
                        .put("service.name", name).put("service.instance.id", UUID.randomUUID().toString()).build())))
                .setSpanLimits(SpanLimits.builder().setMaxNumberOfAttributes(24).setMaxAttributeValueLength(128).build());
        if (!endpoint.isBlank()) builder.addSpanProcessor(BatchSpanProcessor.builder(
                OtlpHttpSpanExporter.builder().setEndpoint(endpoint).setTimeout(Duration.ofSeconds(2)).build())
                .setMaxQueueSize(2048).setMaxExportBatchSize(512).setExporterTimeout(Duration.ofSeconds(3)).build());
        agent.ifAvailable(customizer -> customizer.accept(builder));
        var sdk = OpenTelemetrySdk.builder().setTracerProvider(builder.build()).build();
        GlobalOpenTelemetry.set(sdk); ObservationScope.service(name); ObservationScope.configure(sdk);
        return sdk;
    }
    @PreDestroy public void release() { ObservationScope.configure(null); }
}
