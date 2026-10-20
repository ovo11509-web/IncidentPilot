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

package com.nageoffer.ai.ragent.agent.diagnosis;

import com.nageoffer.ai.ragent.framework.observability.ObservationScope;
import com.nageoffer.ai.ragent.incident.diagnosis.contract.DiagnosisContracts.Begin;
import com.nageoffer.ai.ragent.incident.diagnosis.service.*;
import com.nageoffer.ai.ragent.rag.runbook.*;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.*;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IncidentDiagnosisTraceTest {
    @Test void realLocalStagesAreRecordedAndReusedDiagnosisDoesNotGenerate() {
        List<SpanData> spans = new ArrayList<>();
        var exporter = new SpanExporter() {
            public CompletableResultCode export(Collection<SpanData> batch) { spans.addAll(batch); return CompletableResultCode.ofSuccess(); }
            public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
        try (var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            ObservationScope.configure(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            var records = mock(DiagnosisRecordService.class); var queries = mock(DiagnosisQueryService.class);
            var corpus = new RunbookCorpus(); var snapshot = DiagnosisTestSnapshots.health().put("capturedAt","2026-10-03T12:00:00Z");
            when(records.begin(anyString(),anyString(),anyLong(),anyString(),anyString())).thenReturn(
                    new Begin("101",snapshot,"owner",false,"RUNNING"), new Begin("101",snapshot,null,true,"COMPLETED"));
            when(queries.get("101")).thenReturn(Map.of("requestState","COMPLETED","outcome","SUPPORTED"));
            var generator = new ExtractiveDiagnosisGenerator(new RunbookRetriever(corpus));
            var orchestrator = new IncidentDiagnosisOrchestrator(records,queries,corpus,generator,new DiagnosisEvidenceValidator(generator));
            orchestrator.diagnose("100","request",0);
            assertThat(spans).extracting(SpanData::getName).contains("incident.diagnosis", "incident.runbook.retrieve",
                    "incident.diagnosis.generate", "incident.diagnosis.validate");
            String trace = spans.stream().filter(s -> s.getName().equals("incident.diagnosis")).findFirst().orElseThrow().getTraceId();
            assertThat(spans).allMatch(s -> s.getTraceId().equals(trace));
            spans.clear(); orchestrator.diagnose("100","request",0);
            assertThat(spans).extracting(SpanData::getName).containsExactly("incident.diagnosis");
            assertThat(spans.get(0).getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("ragent.outcome"))).isEqualTo("REUSED");
            assertThat(spans.toString()).doesNotContain("owner", "snapshotDigest", "健康检查");
        } finally { ObservationScope.configure(null); }
    }
}
