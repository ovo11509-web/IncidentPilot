package com.nageoffer.ai.ragent.rag.chat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.config.AIModelProperties;
import com.nageoffer.ai.ragent.convention.ChatMessage;
import com.nageoffer.ai.ragent.convention.ChatRequest;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class BaiLianChatClient implements ChatClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    @Override
    public String provider() {
        return "bailian";
    }

    @Override
    public String chat(ChatRequest request, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = requireProvider(target);

        JsonObject reqBody = new JsonObject();
        reqBody.addProperty("model", requireModel(target));

        JsonArray messages = buildMessages(request);
        reqBody.add("messages", messages);

        if (request.getTemperature() != null) {
            reqBody.addProperty("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            reqBody.addProperty("top_p", request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            reqBody.addProperty("max_tokens", request.getMaxTokens());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(provider.getApiKey());

        HttpEntity<String> httpEntity = new HttpEntity<>(reqBody.toString(), headers);
        ResponseEntity<String> response =
                restTemplate.postForEntity(provider.getUrl(), httpEntity, String.class);

        JsonObject respJson = gson.fromJson(response.getBody(), JsonObject.class);

        return respJson
                .getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content")
                .getAsString();
    }

    @Override
    public StreamHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        doStream(request, callback, cancelled, target);
        return () -> cancelled.set(true);
    }

    private void doStream(ChatRequest request, StreamCallback callback, AtomicBoolean cancelled, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = requireProvider(target);
        HttpURLConnection conn = null;
        try {
            JsonObject reqBody = new JsonObject();
            reqBody.addProperty("model", requireModel(target));
            reqBody.addProperty("stream", true);

            JsonArray messages = buildMessages(request);
            reqBody.add("messages", messages);

            if (request.getTemperature() != null) {
                reqBody.addProperty("temperature", request.getTemperature());
            }
            if (request.getTopP() != null) {
                reqBody.addProperty("top_p", request.getTopP());
            }
            if (request.getMaxTokens() != null) {
                reqBody.addProperty("max_tokens", request.getMaxTokens());
            }
            if (request.getThinking() != null && request.getThinking()) {
                reqBody.addProperty("enable_thinking", true);
            }

            URL url = new URL(provider.getUrl());
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + provider.getApiKey());
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            String jsonBody = gson.toJson(reqBody);
            conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
            conn.getOutputStream().flush();

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while (!cancelled.get() && (line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    String payload = line.trim();
                    if (payload.startsWith("data:")) {
                        payload = payload.substring("data:".length()).trim();
                    }

                    if ("[DONE]".equalsIgnoreCase(payload)) {
                        callback.onComplete();
                        break;
                    }

                    try {
                        JsonObject obj = gson.fromJson(payload, JsonObject.class);
                        JsonArray choices = obj.getAsJsonArray("choices");
                        if (choices == null || choices.isEmpty()) {
                            continue;
                        }

                        JsonObject choice0 = choices.get(0).getAsJsonObject();
                        String chunk = null;

                        if (choice0.has("delta") && choice0.get("delta").isJsonObject()) {
                            JsonObject delta = choice0.getAsJsonObject("delta");
                            if (delta.has("content")) {
                                JsonElement ce = delta.get("content");
                                if (ce != null && !ce.isJsonNull()) {
                                    chunk = ce.getAsString();
                                }
                            }
                        }

                        if (chunk == null && choice0.has("message") && choice0.get("message").isJsonObject()) {
                            JsonObject msg = choice0.getAsJsonObject("message");
                            if (msg.has("content")) {
                                JsonElement ce = msg.get("content");
                                if (ce != null && !ce.isJsonNull()) {
                                    chunk = ce.getAsString();
                                }
                            }
                        }

                        if (chunk != null && !chunk.isEmpty()) {
                            callback.onContent(chunk);
                        }

                        if (choice0.has("finish_reason")) {
                            JsonElement fr = choice0.get("finish_reason");
                            if (fr != null && !fr.isJsonNull()) {
                                callback.onComplete();
                                break;
                            }
                        }

                    } catch (Exception parseEx) {
                        log.warn("百炼流式响应解析失败: payload={}", payload, parseEx);
                    }
                }
            }

        } catch (Exception e) {
            callback.onError(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private JsonArray buildMessages(ChatRequest request) {
        JsonArray arr = new JsonArray();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", request.getSystemPrompt());
            arr.add(sys);
        }

        if (request.getContext() != null && !request.getContext().isEmpty()) {
            JsonObject ctx = new JsonObject();
            ctx.addProperty("role", "system");
            ctx.addProperty("content", "以下是与用户问题相关的背景知识：\n" + request.getContext());
            arr.add(ctx);
        }

        if (request.getHistory() != null) {
            for (ChatMessage m : request.getHistory()) {
                JsonObject msg = new JsonObject();
                msg.addProperty("role", toOpenAiRole(m.getRole()));
                msg.addProperty("content", m.getContent());
                arr.add(msg);
            }
        }

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", request.getPrompt());
        arr.add(userMsg);

        return arr;
    }

    private String toOpenAiRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private AIModelProperties.ProviderConfig requireProvider(ModelTarget target) {
        if (target == null || target.provider() == null || target.provider().getUrl() == null) {
            throw new IllegalStateException("BaiLian provider config is missing");
        }
        return target.provider();
    }

    private String requireModel(ModelTarget target) {
        if (target == null || target.candidate() == null || target.candidate().getModel() == null) {
            throw new IllegalStateException("BaiLian model name is missing");
        }
        return target.candidate().getModel();
    }
}
