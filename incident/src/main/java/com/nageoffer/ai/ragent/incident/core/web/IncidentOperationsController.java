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
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.incident.alert.web.AlertController;
import com.nageoffer.ai.ragent.incident.core.dao.OperationsMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Personnel-only operational snapshot backed by current persisted state. */
@RestController
@RequestMapping("/incident/v1/operations")
public final class IncidentOperationsController {
    private final OperationsMapper operations;

    public IncidentOperationsController(OperationsMapper operations) {
        this.operations = operations;
    }

    @GetMapping("/status")
    public ResponseEntity<Result<Map<String, Object>>> status(HttpServletRequest request) {
        StpUtil.checkLogin();
        StpUtil.checkRole("admin");
        var snapshot = operations.snapshot();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("observedAt", snapshot.getObservedAt());
        data.put("receivedBacklogCount", snapshot.getReceivedBacklogCount());
        data.put("dueRetryCount", snapshot.getDueRetryCount());
        data.put("failedAlertCount", snapshot.getFailedAlertCount());
        data.put("pendingOutboxCount", snapshot.getPendingOutboxCount());
        data.put("expiredOutboxClaimCount", snapshot.getExpiredOutboxClaimCount());
        if (snapshot.getOldestReceivedAgeSeconds() != null)
            data.put("oldestReceivedAgeSeconds", snapshot.getOldestReceivedAgeSeconds());
        if (snapshot.getOldestPendingOutboxAgeSeconds() != null)
            data.put("oldestPendingOutboxAgeSeconds", snapshot.getOldestPendingOutboxAgeSeconds());
        if (snapshot.getLastWorkerSuccessAt() != null)
            data.put("lastWorkerSuccessAt", snapshot.getLastWorkerSuccessAt());
        if (snapshot.getLastPublisherSuccessAt() != null)
            data.put("lastPublisherSuccessAt", snapshot.getLastPublisherSuccessAt());
        data.put("recentFailureCodes", operations.recentFailures().stream().map(f -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("component", f.getComponent());
            item.put("code", f.getCode());
            item.put("observedAt", f.getObservedAt());
            if (f.getAlertId() != null) item.put("alertId", f.getAlertId());
            if (f.getIncidentId() != null) item.put("incidentId", f.getIncidentId());
            if (f.getEventId() != null) item.put("eventId", f.getEventId());
            if (f.getCorrelationId() != null) item.put("correlationId", f.getCorrelationId());
            return item;
        }).toList());
        return ResponseEntity.ok(new Result<Map<String, Object>>().setCode(Result.SUCCESS_CODE)
                .setMessage("OK").setRequestId(AlertController.requestId(request)).setData(data));
    }
}
