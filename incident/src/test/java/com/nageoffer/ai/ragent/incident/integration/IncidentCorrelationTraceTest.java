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

package com.nageoffer.ai.ragent.incident.integration;

import com.nageoffer.ai.ragent.framework.observability.ObservationScope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.*;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentCorrelationTraceTest {
    @Test void nonAnchorFirstBridgesStableIdsWithoutRewritingHistoricalPayloads() throws Exception {
        verifyStableFacts(false);
    }
    @Test void failedSpanExporterPreservesActualAggregationAndDuplicateFacts() throws Exception {
        verifyStableFacts(true);
    }
    private void verifyStableFacts(boolean failExport) throws Exception {
        var spans = new ArrayList<SpanData>();
        var exporter = new SpanExporter() {
            public CompletableResultCode export(Collection<SpanData> data) {
                spans.addAll(data);
                return failExport ? CompletableResultCode.ofFailure() : CompletableResultCode.ofSuccess();
            }
            public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
        try (var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
             var db = new IncidentDbFixture()) {
            ObservationScope.configure(OpenTelemetrySdk.builder().setTracerProvider(provider).build());
            String key = "alert-incident-v1:" + "d".repeat(64);
            var now = db.incidents.databaseNow().minusSeconds(10);
            db.insertAlert("10",key,now); db.insertAlert("20",key,now.plusSeconds(1));
            assertThat(db.aggregation.processOne("20")).isTrue();
            String id = db.alerts.findById("20").getIncidentId();
            assertThat(db.alerts.findById("10").getIncidentId()).isNull();
            String history = db.text("SELECT jsonb_agg(to_jsonb(t) ORDER BY sequence_no)::text FROM t_incident_timeline t");
            String payload = db.text("SELECT jsonb_agg(payload_json ORDER BY event_id)::text FROM t_incident_outbox");
            assertThat(db.aggregation.processOne("20")).isFalse();
            assertThat(db.text("SELECT jsonb_agg(to_jsonb(t) ORDER BY sequence_no)::text FROM t_incident_timeline t")).isEqualTo(history);
            assertThat(db.text("SELECT jsonb_agg(payload_json ORDER BY event_id)::text FROM t_incident_outbox")).isEqualTo(payload);
            assertThat(db.aggregation.processOne("10")).isTrue();
            assertThat(db.alerts.findById("10").getIncidentId()).isEqualTo(id);
            assertThat(db.alerts.findById("10").getCorrelationId()).isEqualTo("910");
            assertThat(db.alerts.findById("20").getCorrelationId()).isEqualTo("920");
            var actual = spans.stream().filter(s -> "incident.alert.process".equals(s.getName())).toList();
            assertThat(actual).hasSize(3);
            assertThat(actual).allMatch(s -> s.getParentSpanId().equals("0000000000000000"));
            assertThat(actual.get(0).getTraceId()).isNotEqualTo(actual.get(2).getTraceId());
            assertThat(actual.get(0).getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("ragent.incident_id"))).isEqualTo(id);
        } finally { ObservationScope.configure(null); }
    }
}
