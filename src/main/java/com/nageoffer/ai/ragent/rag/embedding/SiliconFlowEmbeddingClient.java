package com.nageoffer.ai.ragent.rag.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.config.AIModelProperties;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SiliconFlowEmbeddingClient implements EmbeddingClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    @Override
    public String provider() {
        return "siliconflow";
    }

    @Override
    public List<Float> embed(String text, ModelTarget target) {
        return embedBatch(List.of(text), target).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, ModelTarget target) {
        if (CollectionUtils.isEmpty(texts)) {
            return Collections.emptyList();
        }

        final int maxBatch = 32;
        List<List<Float>> results = new ArrayList<>(Collections.nCopies(texts.size(), null));
        for (int i = 0, n = texts.size(); i < n; i += maxBatch) {
            int end = Math.min(i + maxBatch, n);
            List<String> slice = texts.subList(i, end);
            try {
                List<List<Float>> part = doEmbedOnce(slice, target);
                for (int k = 0; k < part.size(); k++) {
                    results.set(i + k, part.get(k));
                }
            } catch (RestClientResponseException e) {
                String body = e.getResponseBodyAsString();
                log.error("SiliconFlow embeddings HTTP error: status={}, body={}", e.getStatusCode(), body);
                throw new RuntimeException("调用 SiliconFlow Embedding 失败: HTTP " + e.getStatusCode() + " - " + body, e);
            } catch (Exception e) {
                log.error("SiliconFlow embeddings 调用失败", e);
                throw new RuntimeException("调用 SiliconFlow Embedding 失败: " + e.getMessage(), e);
            }
        }

        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
                throw new IllegalStateException("Embedding 结果缺失，index=" + i);
            }
        }
        return results;
    }

    private List<List<Float>> doEmbedOnce(List<String> slice, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = requireProvider(target);
        Map<String, Object> req = new HashMap<>();
        req.put("model", requireModel(target));
        req.put("input", slice);
        if (target.candidate().getDimension() != null) {
            req.put("dimensions", target.candidate().getDimension());
        }
        req.put("encoding_format", "float");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.setBearerAuth(provider.getApiKey());

        HttpEntity<String> entity = new HttpEntity<>(gson.toJson(req), headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(provider.getUrl(), entity, String.class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("SiliconFlow Embedding 非 2xx 响应: " + resp.getStatusCode());
        }

        JsonObject root = JsonParser.parseString(resp.getBody()).getAsJsonObject();

        if (root.has("error")) {
            JsonObject err = root.getAsJsonObject("error");
            String code = err.has("code") ? err.get("code").getAsString() : "unknown";
            String msg = err.has("message") ? err.get("message").getAsString() : "unknown";
            throw new RuntimeException("SiliconFlow Embedding 错误: " + code + " - " + msg);
        }

        JsonArray data = root.getAsJsonArray("data");
        if (data == null) {
            throw new RuntimeException("SiliconFlow Embedding 响应中缺少 data 数组");
        }

        List<List<Float>> vectors = new ArrayList<>(data.size());
        for (JsonElement el : data) {
            JsonObject obj = el.getAsJsonObject();
            JsonArray emb = obj.getAsJsonArray("embedding");
            if (emb == null) {
                throw new RuntimeException("SiliconFlow Embedding 响应中缺少 embedding 字段");
            }

            List<Float> v = new ArrayList<>(emb.size());
            for (JsonElement num : emb) v.add(num.getAsFloat());
            vectors.add(v);
        }

        return vectors;
    }

    private AIModelProperties.ProviderConfig requireProvider(ModelTarget target) {
        if (target == null || target.provider() == null || target.provider().getUrl() == null) {
            throw new IllegalStateException("SiliconFlow provider config is missing");
        }
        return target.provider();
    }

    private String requireModel(ModelTarget target) {
        if (target == null || target.candidate() == null || target.candidate().getModel() == null) {
            throw new IllegalStateException("SiliconFlow model name is missing");
        }
        return target.candidate().getModel();
    }
}
