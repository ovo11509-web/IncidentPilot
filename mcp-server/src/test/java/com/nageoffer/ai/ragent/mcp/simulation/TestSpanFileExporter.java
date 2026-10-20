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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.*;
import io.opentelemetry.sdk.common.CompletableResultCode;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringApplication;
import java.nio.file.*;
import java.util.*;

/** Test-classpath launcher only. Captures actual SpanData without a collector or production extension. */
public final class TestSpanFileExporter implements SpanExporter {
    private final Path file;
    private final ObjectMapper json = new ObjectMapper();
    public TestSpanFileExporter(Path file) { this.file=file; }
    @Override public synchronized CompletableResultCode export(Collection<SpanData> spans) {
        try {
            for(var span:spans) {
                var attributes=new TreeMap<String,Object>(); span.getAttributes().forEach((k,v)->attributes.put(k.getKey(),v));
                var row=new LinkedHashMap<String,Object>();
                row.put("name",span.getName()); row.put("traceId",span.getTraceId()); row.put("spanId",span.getSpanId());
                row.put("parentSpanId",span.getParentSpanId()); row.put("kind",span.getKind().name());
                row.put("startNanos",span.getStartEpochNanos()); row.put("endNanos",span.getEndEpochNanos());
                row.put("service",span.getResource().getAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("service.name")));
                row.put("instance",span.getResource().getAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("service.instance.id")));
                row.put("attributes",attributes);
                Files.writeString(file,json.writeValueAsString(row)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            }
            return CompletableResultCode.ofSuccess();
        } catch(Exception e) { return CompletableResultCode.ofFailure(); }
    }
    public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
    public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
    public static void main(String[] args) {
        var app=new SpringApplication(com.nageoffer.ai.ragent.mcp.McpServerApplication.class);
        app.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            var registry=(BeanDefinitionRegistry)factory;
            if(!registry.containsBeanDefinition("incidentOpenTelemetrySdk")) throw new IllegalStateException("test requires enabled OBS owner");
            var definition=new RootBeanDefinition(OpenTelemetrySdk.class);
            definition.setDestroyMethodName("close");
            definition.setInstanceSupplier(()-> {
                var exporter=new TestSpanFileExporter(Path.of(System.getenv("OBS_TEST_SPAN_FILE")));
                var provider=SdkTracerProvider.builder().setResource(Resource.create(Attributes.builder().put("service.name","incident-mcp").put("service.instance.id",UUID.randomUUID().toString()).build()))
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
                var sdk=OpenTelemetrySdk.builder().setTracerProvider(provider).build();
                GlobalOpenTelemetry.set(sdk); McpObservation.configure(sdk); McpObservation.service("incident-mcp");return sdk;
            });
            registry.removeBeanDefinition("incidentOpenTelemetrySdk"); registry.registerBeanDefinition("incidentOpenTelemetrySdk",definition);
        }));
        app.run(args);
    }
}
