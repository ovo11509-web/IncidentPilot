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

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.incident.core.service.IncidentStateMachine;
import com.nageoffer.ai.ragent.incident.core.service.IncidentTransitionService;
import com.nageoffer.ai.ragent.incident.core.web.IncidentController;
import com.nageoffer.ai.ragent.incident.alert.security.AlertQueryAuth;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReceiveService;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReplayService;
import com.nageoffer.ai.ragent.incident.action.service.ActionDecisionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionProposalService;
import com.nageoffer.ai.ragent.incident.action.web.ActionController;
import com.nageoffer.ai.ragent.incident.action.web.ActionInternalController;
import com.nageoffer.ai.ragent.incident.action.service.ActionTicketConsumptionService;
import com.nageoffer.ai.ragent.incident.action.service.ActionReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Safe HTTP errors for Incident endpoints only. */
@RestControllerAdvice(basePackages = "com.nageoffer.ai.ragent.incident")
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class IncidentErrorAdvice {
    @ExceptionHandler(AlertReceiveService.ConflictException.class)
    public ResponseEntity<Result<Map<String, Object>>> conflict(AlertReceiveService.ConflictException error,
            HttpServletRequest request) {
        var row = error.existing();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retryable", false);
        data.put("alertId", row.getId());
        data.put("correlationId", row.getCorrelationId());
        data.put("processingStatus", row.getProcessingStatus());
        return failure(409, "IDEMPOTENCY_CONFLICT", data, request);
    }

    @ExceptionHandler({AlertReceiveService.PersistenceUnavailableException.class, DataAccessException.class})
    public ResponseEntity<Result<Map<String, Object>>> unavailable(RuntimeException ignored, HttpServletRequest request) {
        return failure(503, "PERSISTENCE_UNAVAILABLE", Map.of("retryable", true), request);
    }

    @ExceptionHandler(AlertQueryAuth.QueryAuthException.class)
    public ResponseEntity<Result<Map<String, Object>>> auth(AlertQueryAuth.QueryAuthException error,
            HttpServletRequest request) {
        String code = switch (error.status()) {
            case 400 -> "BAD_REQUEST";
            case 403 -> "FORBIDDEN";
            case 422 -> "UNSUPPORTED_PROTOCOL_VERSION";
            default -> "UNAUTHORIZED";
        };
        return failure(error.status(), code, Map.of("retryable", false), request);
    }

    @ExceptionHandler(AlertController.NotFoundException.class)
    public ResponseEntity<Result<Map<String, Object>>> notFound(AlertController.NotFoundException ignored,
            HttpServletRequest request) {
        return failure(404, "NOT_FOUND", Map.of("retryable", false), request);
    }

    @ExceptionHandler(AlertReplayService.NotFoundException.class)
    public ResponseEntity<Result<Map<String, Object>>> replayNotFound(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(404, "NOT_FOUND", Map.of("retryable", false), request);
    }

    @ExceptionHandler(AlertReplayService.ConflictException.class)
    public ResponseEntity<Result<Map<String, Object>>> replayConflict(AlertReplayService.ConflictException error,
            HttpServletRequest request) {
        var data = new LinkedHashMap<String, Object>();
        data.put("retryable", false);
        data.put("processingStatus", error.current().getProcessingStatus());
        data.put("currentVersion", error.current().getVersion());
        return failure(409, "REPLAY_CONFLICT", data, request);
    }

    @ExceptionHandler(AlertReplayService.InvalidReplayException.class)
    public ResponseEntity<Result<Map<String, Object>>> invalidReplay(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(422, "INVALID_REPLAY", Map.of("retryable", false), request);
    }

    @ExceptionHandler(AlertController.InvalidReplayJsonException.class)
    public ResponseEntity<Result<Map<String, Object>>> invalidReplayJson(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(400, "INVALID_JSON", Map.of("retryable", false), request);
    }

    @ExceptionHandler({IncidentTransitionService.NotFoundException.class})
    public ResponseEntity<Result<Map<String, Object>>> incidentNotFound(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(404, "NOT_FOUND", Map.of("retryable", false), request);
    }

    @ExceptionHandler(IncidentTransitionService.ConflictException.class)
    public ResponseEntity<Result<Map<String, Object>>> transitionConflict(
            IncidentTransitionService.ConflictException error, HttpServletRequest request) {
        var data = new LinkedHashMap<String, Object>();
        data.put("retryable", false);
        if (error.current() != null) {
            data.put("incidentState", error.current().getLifecycleState());
            data.put("currentVersion", error.current().getVersion());
        }
        return failure(409, "INCIDENT_CONFLICT", data, request);
    }

    @ExceptionHandler(IncidentStateMachine.InvalidTransitionException.class)
    public ResponseEntity<Result<Map<String, Object>>> invalidTransition(
            IncidentStateMachine.InvalidTransitionException error, HttpServletRequest request) {
        return failure(error.status(), "INVALID_TRANSITION", Map.of("retryable", false), request);
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Map<String, Object>>> notLoggedIn(NotLoginException ignored,
            HttpServletRequest request) {
        return failure(401, "UNAUTHORIZED", Map.of("retryable", false), request);
    }

    @ExceptionHandler({ActionDecisionService.ConflictException.class,
            ActionProposalService.ConflictException.class,
            ActionProposalService.SourceConflictException.class})
    public ResponseEntity<Result<Map<String, Object>>> actionConflict(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(409, "ACTION_CONFLICT", Map.of("retryable", false), request);
    }

    @ExceptionHandler(ActionDecisionService.NotFoundException.class)
    public ResponseEntity<Result<Map<String, Object>>> actionNotFound(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(404, "NOT_FOUND", Map.of("retryable", false), request);
    }

    @ExceptionHandler(ActionController.InvalidJsonException.class)
    public ResponseEntity<Result<Map<String, Object>>> actionInvalidJson(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(400, "INVALID_JSON", Map.of("retryable", false), request);
    }

    @ExceptionHandler(ActionInternalController.InvalidJsonException.class)
    public ResponseEntity<Result<Map<String, Object>>> internalInvalidJson(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(400, "INVALID_JSON", Map.of("retryable", false), request);
    }

    @ExceptionHandler(ActionTicketConsumptionService.UnauthorizedException.class)
    public ResponseEntity<Result<Map<String, Object>>> callbackUnauthorized(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(401, "UNAUTHORIZED", Map.of("retryable", false), request);
    }

    @ExceptionHandler(ActionTicketConsumptionService.DeniedException.class)
    public ResponseEntity<Result<Map<String, Object>>> ticketDenied(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(409, "TICKET_DENIED", Map.of("retryable", false), request);
    }

    @ExceptionHandler(ActionReceiptService.ConflictException.class)
    public ResponseEntity<Result<Map<String, Object>>> receiptConflict(RuntimeException ignored,
            HttpServletRequest request) {
        return failure(409, "RECEIPT_CONFLICT", Map.of("retryable", false), request);
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Result<Map<String, Object>>> wrongRole(NotRoleException ignored,
            HttpServletRequest request) {
        return failure(403, "FORBIDDEN", Map.of("retryable", false), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Map<String, Object>>> badArgument(IllegalArgumentException ignored,
            HttpServletRequest request) {
        return failure(422, "INVALID_REQUEST", Map.of("retryable", false), request);
    }

    @ExceptionHandler(IncidentController.InvalidJsonException.class)
    public ResponseEntity<Result<Map<String, Object>>> invalidJson(IncidentController.InvalidJsonException ignored,
            HttpServletRequest request) {
        return failure(400, "INVALID_JSON", Map.of("retryable", false), request);
    }

    private ResponseEntity<Result<Map<String, Object>>> failure(int status, String code, Map<String, Object> data,
            HttpServletRequest request) {
        return ResponseEntity.status(status).body(new Result<Map<String, Object>>()
                .setCode(code).setMessage("Request rejected")
                .setRequestId(AlertController.requestId(request)).setData(data));
    }
}
