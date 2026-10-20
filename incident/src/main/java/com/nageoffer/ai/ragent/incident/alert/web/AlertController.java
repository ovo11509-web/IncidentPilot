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

package com.nageoffer.ai.ragent.incident.alert.web;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer.CanonicalAlert;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.security.AlertQueryAuth;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReceiveService;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReplayService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/incident/v1/alerts")
public final class AlertController {
    private final AlertReceiveService receive;
    private final AlertQueryAuth queryAuth;
    private final AlertMapper alerts;
    private final AlertReplayService replay;
    private final ObjectMapper json = new ObjectMapper();

    public AlertController(AlertReceiveService receive, AlertQueryAuth queryAuth, AlertMapper alerts,
            AlertReplayService replay) {
        this.receive = receive;
        this.queryAuth = queryAuth;
        this.alerts = alerts;
        this.replay = replay;
        json.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        json.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @PostMapping
    public ResponseEntity<Result<Map<String, Object>>> submit(HttpServletRequest request) {
        String requestId = requestId(request);
        Object source = request.getAttribute(AlertIngressFilter.SOURCE_ATTRIBUTE);
        Object canonical = request.getAttribute(AlertIngressFilter.CANONICAL_ATTRIBUTE);
        if (!(source instanceof String sourceId) || !(canonical instanceof CanonicalAlert alert))
            throw new AlertQueryAuth.QueryAuthException(401);
        var submission = receive.receive(sourceId, alert, requestId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("protocolVersion", 1);
        data.put("alertId", submission.alertId());
        data.put("correlationId", submission.correlationId());
        data.put("processingStatus", submission.processingStatus());
        data.put("duplicate", submission.duplicate());
        data.put("replayEligible", submission.replayEligible());
        if (submission.incidentId() != null) data.put("incidentId", submission.incidentId());
        if (submission.failureCode() != null) data.put("failureCode", submission.failureCode());
        int status = "RECEIVED".equals(submission.processingStatus()) ? 202 : 200;
        return ResponseEntity.status(status).body(success(data, requestId));
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<Result<Map<String, Object>>> status(@PathVariable String alertId, HttpServletRequest request) {
        var principal = queryAuth.authenticate(request, alertId);
        AlertMapper.AlertRow row = alerts.findById(alertId);
        if (row == null || (!principal.staff() && !row.getSourceId().equals(principal.sourceId())))
            throw new NotFoundException();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alertId", row.getId());
        data.put("correlationId", row.getCorrelationId());
        data.put("sourceId", row.getSourceId());
        data.put("processingStatus", row.getProcessingStatus());
        data.put("replayEligible", "FAILED".equals(row.getProcessingStatus()));
        data.put("version", row.getVersion());
        data.put("attemptCount", row.getAttemptCount());
        data.put("retryCount", row.getRetryCount());
        if (row.getNextAttemptAt() != null) data.put("nextAttemptAt", row.getNextAttemptAt());
        if (row.getFailureCode() != null) data.put("failureCode", row.getFailureCode());
        if (row.getFailureSummary() != null) data.put("failureSummary", row.getFailureSummary());
        if (row.getFailedAt() != null) data.put("failedAt", row.getFailedAt());
        if (row.getIncidentId() != null) data.put("incidentId", row.getIncidentId());
        return ResponseEntity.ok(success(data, requestId(request)));
    }

    @PostMapping("/{alertId}/replay")
    public ResponseEntity<Result<Map<String, Object>>> replay(@PathVariable String alertId,
            @RequestBody String body, HttpServletRequest request) {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
        if (!alertId.matches("[0-9]{1,20}")) throw new IllegalArgumentException();
        com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = json.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new InvalidReplayJsonException();
        }
        if (root == null || !root.isObject() || root.size() != 3
                || !root.hasNonNull("expectedStatus") || !"FAILED".equals(root.get("expectedStatus").asText())
                || !root.hasNonNull("expectedVersion") || !root.get("expectedVersion").canConvertToLong()
                || root.get("expectedVersion").longValue() < 0
                || !root.hasNonNull("reason") || !root.get("reason").isTextual())
            throw new AlertReplayService.InvalidReplayException();
        String requestId = requestId(request);
        var saved = replay.replay(alertId, root.get("expectedVersion").longValue(),
                root.get("reason").textValue(), StpUtil.getLoginIdAsString(), requestId);
        return ResponseEntity.status(202).body(success(Map.of("alertId", saved.getId(),
                "correlationId", saved.getCorrelationId(), "processingStatus", saved.getProcessingStatus(),
                "replayEligible", false, "version", saved.getVersion()), requestId));
    }

    public static String requestId(HttpServletRequest request) {
        Object assigned = request.getAttribute(AlertIngressFilter.REQUEST_ID_ATTRIBUTE);
        return com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId(request);
    }

    static <T> Result<T> success(T data, String requestId) {
        return new Result<T>().setCode(Result.SUCCESS_CODE).setMessage("OK").setRequestId(requestId).setData(data);
    }

    public static final class NotFoundException extends RuntimeException {}
    public static final class InvalidReplayJsonException extends RuntimeException {}
}
