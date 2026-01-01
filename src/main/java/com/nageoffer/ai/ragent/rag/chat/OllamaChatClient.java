package com.nageoffer.ai.ragent.rag.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.config.AIModelProperties;
import com.nageoffer.ai.ragent.convention.ChatMessage;
import com.nageoffer.ai.ragent.convention.ChatRequest;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
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
public class OllamaChatClient implements ChatClient {

    private final Gson gson = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String chat(ChatRequest request, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = requireProvider(target);
        String url = provider.getUrl() + "/api/chat";

        JsonObject body = new JsonObject();
        body.addProperty("model", requireModel(target));
        body.addProperty("stream", false);

        JsonArray messages = buildMessages(request);
        body.add("messages", messages);

        if (request.getTemperature() != null) {
            body.addProperty("temperature", request.getTemperature());
        }
        if (request.getTopP() != null) {
            body.addProperty("top_p", request.getTopP());
        }
        if (request.getMaxTokens() != null) {
            body.addProperty("num_predict", request.getMaxTokens());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> req = new HttpEntity<>(body.toString(), headers);

        ResponseEntity<String> resp =
                restTemplate.postForEntity(url, req, String.class);

        JsonObject json = gson.fromJson(resp.getBody(), JsonObject.class);

        return json
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
            JsonObject body = new JsonObject();
            body.addProperty("model", requireModel(target));
            body.addProperty("stream", true);

            JsonArray messages = buildMessages(request);
            body.add("messages", messages);

            if (request.getTemperature() != null) {
                body.addProperty("temperature", request.getTemperature());
            }
            if (request.getTopP() != null) {
                body.addProperty("top_p", request.getTopP());
            }
            if (request.getMaxTokens() != null) {
                body.addProperty("num_predict", request.getMaxTokens());
            }

            URL url = new URL(provider.getUrl() + "/api/chat");
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            String jsonBody = gson.toJson(body);
            conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
            conn.getOutputStream().flush();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while (!cancelled.get() && (line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    JsonObject obj = gson.fromJson(line, JsonObject.class);

                    if (obj.has("done") && obj.get("done").getAsBoolean()) {
                        callback.onComplete();
                        break;
                    }

                    if (obj.has("message")) {
                        JsonObject msg = obj.getAsJsonObject("message");
                        if (msg.has("content")) {
                            String chunk = msg.get("content").getAsString();
                            if (!chunk.isEmpty()) {
                                callback.onContent(chunk);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            callback.onError(e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private JsonArray buildMessages(ChatRequest request) {
        JsonArray arr = new JsonArray();

        if (request.getSystemPrompt() != null &&
                !request.getSystemPrompt().isEmpty()) {
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
                msg.addProperty("role", toOllamaRole(m.getRole()));
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

    private String toOllamaRole(ChatMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private AIModelProperties.ProviderConfig requireProvider(ModelTarget target) {
        if (target == null || target.provider() == null || target.provider().getUrl() == null) {
            throw new IllegalStateException("Ollama provider config is missing");
        }
        return target.provider();
    }

    private String requireModel(ModelTarget target) {
        if (target == null || target.candidate() == null || target.candidate().getModel() == null) {
            throw new IllegalStateException("Ollama model name is missing");
        }
        return target.candidate().getModel();
    }
}
