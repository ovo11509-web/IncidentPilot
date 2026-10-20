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

package com.nageoffer.ai.ragent.incident.core.web;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.core.dao.IncidentMapper;
import com.nageoffer.ai.ragent.incident.core.service.EpisodePartitioner;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import com.nageoffer.ai.ragent.incident.timeline.dao.TimelineMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Personnel-only Incident snapshot, lifecycle command and append-only Timeline view. */
@RestController
@RequestMapping("/incident/v1/incidents")
public final class IncidentController {
    private final IncidentMapper incidents;
    private final AlertMapper alerts;
    private final TimelineMapper timeline;
    private final IncidentTransitionService transitions;
    private final EpisodePartitioner partitioner;
    private final ObjectMapper json = new ObjectMapper();
    private com.nageoffer.ai.ragent.incident.core.service.IncidentQueryService queries;

    @org.springframework.beans.factory.annotation.Autowired
    public void setQueryService(com.nageoffer.ai.ragent.incident.core.service.IncidentQueryService queries) {
        this.queries=queries;
    }

    @GetMapping
    public ResponseEntity<Result<Map<String,Object>>> list(@RequestParam(defaultValue="20") int limit,
            @RequestParam(required=false) String state, @RequestParam(required=false) String afterEpisodeStartAt,
            @RequestParam(required=false) String afterId, @RequestParam(required=false) String q,
            @RequestParam(required=false) String service, @RequestParam(required=false) String environment,
            @RequestParam(required=false) String severity, HttpServletRequest request) {
        staff();
        return ResponseEntity.ok(success(queries.list(limit,state,afterEpisodeStartAt,afterId,q,service,environment,severity),requestId(request)));
    }

    @GetMapping("/{incidentId}/alerts")
    public ResponseEntity<Result<Map<String,Object>>> alerts(@PathVariable String incidentId,
            @RequestParam(defaultValue="20") int limit, @RequestParam(required=false) String afterId,
            HttpServletRequest request) {
        staff();validId(incidentId);
        return ResponseEntity.ok(success(queries.alerts(incidentId,limit,afterId),requestId(request)));
    }

    public IncidentController(IncidentMapper incidents, AlertMapper alerts, TimelineMapper timeline,
            IncidentTransitionService transitions, EpisodePartitioner partitioner) {
        this.incidents = incidents;
        this.alerts = alerts;
        this.timeline = timeline;
        this.transitions = transitions;
        this.partitioner = partitioner;
        this.json.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.json.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @GetMapping("/{incidentId}")
    public ResponseEntity<Result<Map<String, Object>>> get(@PathVariable String incidentId,
            HttpServletRequest request) {
        staff();
        validId(incidentId);
        return ResponseEntity.ok(success(snapshot(incidentId), requestId(request)));
    }

    @PostMapping("/{incidentId}/transitions")
    public ResponseEntity<Result<Map<String, Object>>> transition(@PathVariable String incidentId,
            @RequestBody String requestBody, HttpServletRequest request) {
        staff();
        validId(incidentId);
        JsonNode body;
        try {
            body = json.readTree(requestBody);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new InvalidJsonException();
        }
        if (body == null) throw new InvalidJsonException();
        if (!body.isObject() || body.size() < 3 || body.size() > 4) throw new IllegalArgumentException();
        body.fieldNames().forEachRemaining(name -> {
            if (!Set.of("expectedState", "expectedVersion", "targetState", "reason").contains(name))
                throw new IllegalArgumentException();
        });
        if (!body.hasNonNull("expectedState") || !body.get("expectedState").isTextual()
                || !body.hasNonNull("targetState") || !body.get("targetState").isTextual()
                || !body.hasNonNull("expectedVersion") || !body.get("expectedVersion").canConvertToLong()
                || body.get("expectedVersion").longValue() < 0
                || (body.has("reason") && !body.get("reason").isTextual())) throw new IllegalArgumentException();
        String requestId = requestId(request);
        transitions.transition(incidentId, body.get("expectedState").textValue(),
                body.get("expectedVersion").longValue(), body.get("targetState").textValue(),
                body.has("reason") ? body.get("reason").textValue() : null,
                StpUtil.getLoginIdAsString(), requestId);
        return ResponseEntity.ok(success(snapshot(incidentId), requestId));
    }

    @GetMapping("/{incidentId}/timeline")
    public ResponseEntity<Result<Map<String, Object>>> timeline(@PathVariable String incidentId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "100") int limit, HttpServletRequest request) {
        staff();
        validId(incidentId);
        if (afterSequence < 0 || limit < 1 || limit > 200) throw new IllegalArgumentException();
        if (incidents.findById(incidentId) == null) throw new IncidentTransitionService.NotFoundException();
        List<TimelineMapper.Fact> fetched = timeline.findPage(incidentId, afterSequence, limit + 1);
        boolean more = fetched.size() > limit;
        List<Map<String, Object>> items = new ArrayList<>();
        long next = afterSequence;
        for (int i = 0; i < Math.min(limit, fetched.size()); i++) {
            var fact = fetched.get(i);
            next = fact.getSequenceNo();
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("eventId", fact.getId());
            view.put("sequenceNo", fact.getSequenceNo());
            view.put("eventType", fact.getEventType());
            view.put("actorType", fact.getActorType());
            view.put("actorId", fact.getActorId());
            view.put("occurredAt", fact.getOccurredAt().toString());
            view.put("correlationId", fact.getCorrelationId());
            if (fact.getRelatedEntityType() != null) view.put("relatedEntityType", fact.getRelatedEntityType());
            if (fact.getRelatedEntityId() != null) view.put("relatedEntityId", fact.getRelatedEntityId());
            try {
                view.put("summary", json.readValue(fact.getSummaryJson(), Map.class));
            } catch (Exception e) {
                throw new IllegalStateException("Timeline summary could not be read", e);
            }
            items.add(view);
        }
        return ResponseEntity.ok(success(Map.of("incidentId", incidentId, "items", items,
                "nextAfterSequence", next, "hasMore", more), requestId(request)));
    }

    private Map<String, Object> snapshot(String id) {
        var row = incidents.findById(id);
        if (row == null) throw new IncidentTransitionService.NotFoundException();
        var members = alerts.findAllByAggregationKey(row.getAggregationKey());
        var seals = incidents.findAllByAggregationKey(row.getAggregationKey()).stream()
                .map(IncidentMapper.IncidentRow::getLifecycleSealedAt).filter(Objects::nonNull).toList();
        var episodes = partitioner.partition(members.stream().map(alert ->
                new EpisodePartitioner.AlertPoint(alert.getId(), alert.getReceivedAt(), alert.getProcessingStatus()))
                .toList(), seals, Duration.ofSeconds(row.getAggregationWindowSeconds()));
        boolean open = row.getLifecycleSealedAt() == null && !row.isBornSealed()
                && episodes.stream().filter(e -> e.anchor().id().equals(row.getEpisodeAnchorAlertId()))
                        .findFirst().map(e -> !incidents.databaseNow().isAfter(e.members()
                                .get(e.members().size() - 1).receivedAt()
                                .plusSeconds(row.getAggregationWindowSeconds()))).orElse(false);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("incidentId", row.getId());
        data.put("aggregationKey", row.getAggregationKey());
        data.put("aggregationRuleVersion", row.getAggregationRuleVersion());
        data.put("aggregationWindowSeconds", row.getAggregationWindowSeconds());
        data.put("episodeStartAt", row.getEpisodeStartAt().toString());
        data.put("episodeAnchorAlertId", row.getEpisodeAnchorAlertId());
        data.put("firstAlertReceivedAt", row.getFirstAlertReceivedAt().toString());
        data.put("lastAlertReceivedAt", row.getLastAlertReceivedAt().toString());
        data.put("alertCount", row.getAlertCount());
        data.put("lifecycleState", row.getLifecycleState());
        if (row.getLifecycleSealedAt() != null) data.put("lifecycleSealedAt", row.getLifecycleSealedAt().toString());
        data.put("aggregationOpen", open);
        data.put("bornSealed", row.isBornSealed());
        data.put("version", row.getVersion());
        return data;
    }

    private static void staff() {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
    }

    private static void validId(String id) {
        if (!id.matches("[0-9]{1,20}")) throw new IllegalArgumentException();
    }

    private static String requestId(HttpServletRequest request) {
        return com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId(request);
    }

    private static <T> Result<T> success(T data, String requestId) {
        return new Result<T>().setCode(Result.SUCCESS_CODE).setMessage("OK")
                .setRequestId(requestId).setData(data);
    }

    public static final class InvalidJsonException extends RuntimeException {}
}
