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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer;
import com.nageoffer.ai.ragent.incident.alert.security.SourceHmacVerifier;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies the POST precedence table before a persistence transaction can begin. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class AlertIngressFilter extends OncePerRequestFilter {
    public static final String SOURCE_ATTRIBUTE = "incident.alert.source";
    public static final String CANONICAL_ATTRIBUTE = "incident.alert.canonical";
    public static final String REQUEST_ID_ATTRIBUTE = "incident.alert.requestId";
    private static final String SUBMIT_PATH = "/incident/v1/alerts";
    private final IncidentProperties properties;
    private final SourceHmacVerifier verifier;
    private final AlertV1Canonicalizer canonicalizer;
    private final ObjectMapper json = new ObjectMapper();

    public AlertIngressFilter(IncidentProperties properties, SourceHmacVerifier verifier,
            AlertV1Canonicalizer canonicalizer) {
        this.properties = properties;
        this.verifier = verifier;
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!"POST".equals(request.getMethod()) || !SUBMIT_PATH.equals(path)) {
            chain.doFilter(request, response);
            return;
        }
        String requestId = com.nageoffer.ai.ragent.framework.observability.ObservationScope.requestId(request);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        if (!supportedContentType(request.getContentType())) {
            reject(response, 415, "UNSUPPORTED_MEDIA_TYPE", requestId);
            return;
        }
        byte[] body;
        try {
            body = request.getInputStream().readNBytes(properties.getIngress().getMaxBodyBytes() + 1);
        } catch (IOException e) {
            reject(response, 503, "INGRESS_UNAVAILABLE", requestId);
            return;
        }
        if (body.length > properties.getIngress().getMaxBodyBytes()) {
            reject(response, 413, "PAYLOAD_TOO_LARGE", requestId);
            return;
        }
        if (request.getQueryString() != null || request.getHeader("X-HTTP-Method-Override") != null) {
            reject(response, 401, "UNAUTHORIZED", requestId);
            return;
        }
        SourceHmacVerifier.Identity identity;
        try {
            identity = verifier.verify(request, body, SUBMIT_PATH);
        } catch (SourceHmacVerifier.AuthenticationException e) {
            reject(response, 401, "UNAUTHORIZED", requestId);
            return;
        }
        com.fasterxml.jackson.databind.JsonNode structure;
        try {
            structure = canonicalizer.parseStructure(body);
        } catch (AlertV1Canonicalizer.InvalidJsonException e) {
            reject(response, 400, "INVALID_JSON", requestId);
            return;
        }
        if (identity.protocolVersion() != 1) {
            reject(response, 422, "UNSUPPORTED_PROTOCOL_VERSION", requestId);
            return;
        }
        try {
            request.setAttribute(CANONICAL_ATTRIBUTE, canonicalizer.canonicalize(structure));
        } catch (AlertV1Canonicalizer.InvalidContractException e) {
            reject(response, 422, "INVALID_ALERT", requestId);
            return;
        }
        request.setAttribute(SOURCE_ATTRIBUTE, identity.sourceId());
        chain.doFilter(request, response);
    }

    private static boolean supportedContentType(String value) {
        if (value == null) return false;
        try {
            MediaType type = MediaType.parseMediaType(value);
            if (!MediaType.APPLICATION_JSON.equalsTypeAndSubtype(type)) return false;
            if (type.getParameters().isEmpty()) return true;
            return type.getParameters().size() == 1 && "utf-8".equalsIgnoreCase(type.getParameter("charset"));
        } catch (Exception e) {
            return false;
        }
    }

    private void reject(HttpServletResponse response, int status, String code, String requestId) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<Map<String, Boolean>> result = new Result<Map<String, Boolean>>()
                .setCode(code).setMessage("Request rejected").setRequestId(requestId)
                .setData(Map.of("retryable", status == 503));
        response.getOutputStream().write(json.writeValueAsBytes(result));
    }
}
