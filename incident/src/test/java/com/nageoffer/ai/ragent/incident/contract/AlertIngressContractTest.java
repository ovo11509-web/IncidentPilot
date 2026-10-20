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

package com.nageoffer.ai.ragent.incident.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.nageoffer.ai.ragent.incident.alert.contract.AlertV1Canonicalizer;
import com.nageoffer.ai.ragent.incident.alert.security.SourceHmacVerifier;
import com.nageoffer.ai.ragent.incident.alert.security.AlertQueryAuth;
import com.nageoffer.ai.ragent.incident.alert.dao.AlertMapper;
import com.nageoffer.ai.ragent.incident.alert.service.AlertReceiveService;
import com.nageoffer.ai.ragent.incident.alert.web.AlertController;
import com.nageoffer.ai.ragent.incident.alert.web.AlertIngressFilter;
import com.nageoffer.ai.ragent.incident.alert.web.IncidentErrorAdvice;
import com.nageoffer.ai.ragent.incident.config.IncidentProperties;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertIngressContractTest {

    private static final String PATH = "/incident/v1/alerts";
    private static final byte[] VALID_BODY = ("{\"externalAlertId\":\"a-1\",\"eventType\":\"FIRING\","
            + "\"occurredAt\":\"2026-09-23T00:00:00Z\",\"ruleId\":\"cpu\","
            + "\"title\":\"CPU high\",\"severity\":\"HIGH\",\"environment\":\"prod\","
            + "\"service\":\"api\",\"resource\":{\"type\":\"host\",\"id\":\"h-1\"}}")
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void mediaTypeWinsOverSizeAndAuthentication() throws Exception {
        var result = send("text/plain", new byte[65_537], false);
        assertEquals(415, result.response().getStatus());
        assertFalse(result.forwarded());
    }

    @Test
    void actualBytesWinOverAuthenticationAndJson() throws Exception {
        var result = send("application/json", new byte[65_537], false);
        assertEquals(413, result.response().getStatus());
        assertFalse(result.forwarded());
    }

    @Test
    void authenticationWinsOverMalformedJson() throws Exception {
        var result = send("application/json", "{".getBytes(StandardCharsets.UTF_8), false);
        assertEquals(401, result.response().getStatus());
        assertFalse(result.forwarded());
    }

    @Test
    void validAuthenticationThenMalformedJsonIsBadRequest() throws Exception {
        var result = send("application/json", "{".getBytes(StandardCharsets.UTF_8), true);
        assertEquals(400, result.response().getStatus());
        assertFalse(result.forwarded());
    }

    @Test
    void validAuthenticationThenInvalidFieldsIsUnprocessable() throws Exception {
        var result = send("application/json", "{}".getBytes(StandardCharsets.UTF_8), true);
        assertEquals(422, result.response().getStatus());
        assertFalse(result.forwarded());
    }

    @Test
    void acceptedRequestReachesPersistenceBoundaryWithSafeAttributes() throws Exception {
        var result = send("application/json; charset=utf-8", VALID_BODY, true);
        assertTrue(result.forwarded());
        assertEquals(202, result.response().getStatus());
    }

    @Test
    void realHttpStatusesReflectCommittedStateConflictAndUnavailableStorage() throws Exception {
        var properties = new IncidentProperties();
        var verifier = mock(SourceHmacVerifier.class);
        when(verifier.verify(any(), any(), any())).thenReturn(new SourceHmacVerifier.Identity("source", 1));
        var service = mock(AlertReceiveService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new AlertController(service, mock(AlertQueryAuth.class),
                        mock(AlertMapper.class), mock(com.nageoffer.ai.ragent.incident.alert.service.AlertReplayService.class)))
                .setControllerAdvice(new IncidentErrorAdvice())
                .addFilters(new AlertIngressFilter(properties, verifier, new AlertV1Canonicalizer(properties)))
                .build();
        when(service.receive(anyString(), any(), anyString())).thenReturn(
                new AlertReceiveService.Submission("101", "201", "RECEIVED", false, false, null, null));
        mvc.perform(post(PATH).contentType("application/json").content(VALID_BODY))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.alertId").value("101"))
                .andExpect(jsonPath("$.data.processingStatus").value("RECEIVED"));
        when(service.receive(anyString(), any(), anyString())).thenReturn(
                new AlertReceiveService.Submission("101", "201", "FAILED", true, true, null, "SAFE_FAILURE"));
        mvc.perform(post(PATH).contentType("application/json").content(VALID_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.replayEligible").value(true));
        var existing = new AlertMapper.AlertRow();
        existing.setId("101");
        existing.setCorrelationId("201");
        existing.setProcessingStatus("RECEIVED");
        when(service.receive(anyString(), any(), anyString()))
                .thenThrow(new AlertReceiveService.ConflictException(existing));
        mvc.perform(post(PATH).contentType("application/json").content(VALID_BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.alertId").value("101"))
                .andExpect(jsonPath("$.data.retryable").value(false));
        doThrow(new AlertReceiveService.PersistenceUnavailableException())
                .when(service).receive(anyString(), any(), anyString());
        mvc.perform(post(PATH).contentType("application/json").content(VALID_BODY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.retryable").value(true))
                .andExpect(jsonPath("$.data.alertId").doesNotExist());
    }

    private Outcome send(String contentType, byte[] body, boolean authenticated) throws Exception {
        var properties = new IncidentProperties();
        var verifier = mock(SourceHmacVerifier.class);
        if (authenticated) {
            when(verifier.verify(any(), any(), any())).thenReturn(new SourceHmacVerifier.Identity("source", 1));
        } else {
            when(verifier.verify(any(), any(), any())).thenThrow(new SourceHmacVerifier.AuthenticationException());
        }
        var filter = new AlertIngressFilter(properties, verifier, new AlertV1Canonicalizer(properties));
        var request = new MockHttpServletRequest("POST", PATH);
        request.setContentType(contentType);
        request.setContent(body);
        var response = new MockHttpServletResponse();
        var forwarded = new AtomicBoolean();
        FilterChain chain = (req, res) -> {
            forwarded.set(true);
            ((MockHttpServletResponse) res).setStatus(202);
        };
        filter.doFilter(request, response, chain);
        return new Outcome(response, forwarded.get());
    }

    private record Outcome(MockHttpServletResponse response, boolean forwarded) {}
}
