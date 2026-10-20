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

package com.nageoffer.ai.ragent.infra.embedding;

import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.http.ModelClientException;
import com.nageoffer.ai.ragent.infra.http.ModelClientErrorType;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SiliconFlowEmbeddingClientTest {
    private HttpServer server;
    private final AtomicReference<String> request = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(new OkHttpClient());

    @AfterEach
    void closeServer() { if (server != null) server.stop(0); }

    @Test
    void sendsConfiguredProtocolAndReturnsExactVector() throws IOException {
        serve(200, "{\"data\":[{\"embedding\":[0.25,-0.5]}]}");
        assertEquals(List.of(0.25F, -0.5F), client.embed("fixed-text", target()));
        var body = JsonParser.parseString(request.get()).getAsJsonObject();
        assertEquals("POST", method.get());
        assertEquals("Bearer local-test-key", authorization.get());
        assertEquals("fixed-model", body.get("model").getAsString());
        assertEquals(2, body.get("dimensions").getAsInt());
        assertEquals("float", body.get("encoding_format").getAsString());
        assertEquals("fixed-text", body.getAsJsonArray("input").get(0).getAsString());
        assertEquals(1, calls.get());
    }

    @Test
    void batchPreservesConfiguredInputAndResponseOrder() throws IOException {
        serve(200, "{\"data\":[{\"embedding\":[1,0]},{\"embedding\":[0,1]}]}");
        assertEquals(List.of(List.of(1F,0F), List.of(0F,1F)), client.embedBatch(List.of("one","two"), target()));
        var input = JsonParser.parseString(request.get()).getAsJsonObject().getAsJsonArray("input");
        assertEquals(List.of("one","two"), input.asList().stream().map(v -> v.getAsString()).toList());
        assertEquals(1, calls.get());
    }

    @Test
    void emptyBatchDoesNotSendHttp() throws IOException {
        serve(200, "{\"data\":[]}");
        assertEquals(List.of(), client.embedBatch(List.of(), target()));
        assertEquals(0, calls.get());
    }

    @Test
    void rateLimitIsTypedAndNotRetriedByClient() throws IOException {
        serve(429, "fixed-rate-limit");
        var error = assertThrows(ModelClientException.class, () -> client.embed("fixed", target()));
        assertEquals(ModelClientErrorType.RATE_LIMITED, error.getErrorType());
        assertEquals(429, error.getStatusCode());
        assertEquals(1, calls.get());
    }

    @Test
    void missingVectorsAreInvalidResponse() throws IOException {
        serve(200, "{\"data\":[{\"embedding\":[]}]}");
        var error = assertThrows(ModelClientException.class, () -> client.embed("fixed", target()));
        assertEquals(ModelClientErrorType.INVALID_RESPONSE, error.getErrorType());
        assertEquals(1, calls.get());
    }

    private void serve(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/embeddings", exchange -> {
            calls.incrementAndGet();
            request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            method.set(exchange.getRequestMethod());
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
            finally { exchange.close(); }
        });
        server.start();
    }

    private ModelTarget target() {
        var provider = new AIModelProperties.ProviderConfig();
        provider.setUrl("http://127.0.0.1:"+server.getAddress().getPort());
        provider.setApiKey("local-test-key");
        provider.setEndpoints(Map.of("embedding","/embeddings"));
        var candidate = new AIModelProperties.ModelCandidate();
        candidate.setModel("fixed-model"); candidate.setDimension(2);
        return new ModelTarget("fixed-model",candidate,provider,null);
    }
}
