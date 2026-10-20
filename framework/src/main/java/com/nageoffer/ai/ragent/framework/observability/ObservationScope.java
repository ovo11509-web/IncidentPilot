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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.*;

/** Content-free auxiliary evidence. Never participates in business validation or persistence. */
public final class ObservationScope implements AutoCloseable {
    public static final String REQUEST_ID_ATTRIBUTE = "incident.alert.requestId";
    private static final Set<String> KEYS = Set.of("service", "operation", "phase", "outcome", "reasonCode",
            "requestId", "traceId", "spanId", "incidentId", "alertId", "correlationId", "diagnosisId",
            "actionId", "approvalId", "executionId", "dispatchRequestId", "sourceRequestId", "receiptId",
            "eventId", "attempt", "processingEpoch");
    private static volatile OpenTelemetry telemetry = OpenTelemetry.noop();
    private static volatile boolean enabled;
    private static volatile String service = "ragent";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ContextKey<Boolean> ACTIVE = ContextKey.named("incident.observation.active");
    private final Map<String, String> previous = MDC.getCopyOfContextMap();
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final Span span;
    private final Scope current;
    private boolean detached;
    private boolean closed;

    public static void configure(OpenTelemetry sdk) { telemetry = sdk == null ? OpenTelemetry.noop() : sdk; enabled = sdk != null; }
    public static void service(String name) { if (safe(name)) service = name; }
    private ObservationScope(String operation, SpanKind kind, Context parent, boolean inherit) {
        if (!operation.matches("incident\\.[a-z.]+")) throw new IllegalArgumentException("invalid observation operation");
        span = telemetry.getTracer("com.nageoffer.ai.ragent.incident")
                .spanBuilder(operation).setSpanKind(kind).setParent(parent).startSpan();
        current = Context.current().with(span).with(ACTIVE, true).makeCurrent();
        if (inherit && previous != null) previous.forEach((k, v) -> { if (KEYS.contains(k) && safe(v)) fields.put(k, v); });
        fields.remove("traceId"); fields.remove("spanId"); fields.remove("outcome"); fields.remove("reasonCode");
        MDC.setContextMap(fields); fields.remove("outcome"); fields.remove("reasonCode");
        id("service", service); id("operation", operation); id("phase", "complete");
        if (span.isRecording()) { id("traceId", span.getSpanContext().getTraceId()); id("spanId", span.getSpanContext().getSpanId()); }
        Map.copyOf(fields).forEach(this::id);
    }
    public static ObservationScope start(String operation, SpanKind kind) { return new ObservationScope(operation, kind, Context.current(), true); }
    public static ObservationScope root(String operation, SpanKind kind, Context parent) { return new ObservationScope(operation, kind, parent, false); }
    public ObservationScope id(String key, Object value) {
        if (value == null || !KEYS.contains(key) || fields.size() >= 24 && !fields.containsKey(key)) return this;
        String text = value.toString();
        if (!safe(text)) return this;
        fields.put(key, text); if (!detached) MDC.put(key, text);
        if (!key.equals("traceId") && !key.equals("spanId"))
            span.setAttribute("ragent." + key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT), text);
        return this;
    }
    public ObservationScope outcome(String value) { return id("outcome", value); }
    public Map<String, String> fields() { return Map.copyOf(fields); }
    public static void identify(String key, Object value) {
        if (!Boolean.TRUE.equals(Context.current().get(ACTIVE))) return;
        if (value != null && KEYS.contains(key) && safe(value.toString())) {
            MDC.put(key, value.toString());
            Span.current().setAttribute("ragent." + key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT), value.toString());
        }
    }
    private static boolean safe(String value) { return value != null && value.matches("[A-Za-z0-9._:-]{1,128}"); }
    public void detach() {
        if (!detached) { detached = true; current.close(); restore(previous); }
    }
    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            // Only fields belonging to this operation; no arbitrary MDC or exception serializer.
            if (!detached) { var mdc = MDC.getCopyOfContextMap();
                if (mdc != null) mdc.forEach((k,v) -> { if (KEYS.contains(k) && safe(v)) fields.put(k,v); }); }
            LoggerFactory.getLogger("incident.observation").info(JSON.writeValueAsString(fields));
        } catch (Exception ignored) { /* Auxiliary logging cannot decide a business outcome. */ }
        finally { detach(); span.end(); }
    }
    private static void restore(Map<String,String> map) { if (map == null) MDC.clear(); else MDC.setContextMap(map); }
    public static String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String id) return id;
        String id = UUID.randomUUID().toString(); request.setAttribute(REQUEST_ID_ATTRIBUTE, id); return id;
    }
    public static String requestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) return requestId(attrs.getRequest());
        String existing = MDC.get("requestId"); return existing != null ? existing : UUID.randomUUID().toString();
    }
    public record Snapshot(Context context, Map<String,String> fields) {
        public Scope makeCurrent() {
            var previous = MDC.getCopyOfContextMap(); var current = context.makeCurrent();
            restore(fields); return () -> { current.close(); restore(previous); };
        }
    }
    public static Snapshot capture() {
        Map<String,String> fields = new LinkedHashMap<>(); var map = MDC.getCopyOfContextMap();
        if (map != null) map.forEach((k,v) -> { if (KEYS.contains(k) && safe(v)) fields.put(k,v); });
        return new Snapshot(Context.current(), Map.copyOf(fields));
    }
    public static Context extract(HttpServletRequest request, boolean authenticated) {
        var values = Collections.list(request.getHeaders("traceparent"));
        if (!authenticated || values.size() != 1 || values.get(0).length() > 256) return Context.root();
        return W3CTraceContextPropagator.getInstance().extract(Context.root(), request, new TextMapGetter<>() {
            public Iterable<String> keys(HttpServletRequest r) { return List.of("traceparent"); }
            public String get(HttpServletRequest r, String key) { return "traceparent".equals(key) ? values.get(0) : null; }
        });
    }
    public static void inject(Context parent, java.util.function.BiConsumer<String,String> setter) {
        if (!enabled) return;
        W3CTraceContextPropagator.getInstance().inject(parent, setter, (carrier,k,v) -> { if (k.equals("traceparent")) carrier.accept(k,v); });
    }
}
