package com.nageoffer.ai.ragent.rag.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.config.AIModelProperties;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public List<Float> embed(String text, ModelTarget target) {
        AIModelProperties.ProviderConfig provider = requireProvider(target);
        String url = provider.getUrl() + "/api/embed";

        JsonObject body = new JsonObject();
        body.addProperty("model", requireModel(target));
        body.addProperty("input", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(body.toString(), headers);

        ResponseEntity<String> resp =
                restTemplate.postForEntity(url, entity, String.class);

        JsonObject json = gson.fromJson(resp.getBody(), JsonObject.class);

        var embeddings = json.getAsJsonArray("embeddings");

        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalStateException("No embeddings returned from Ollama");
        }

        var first = embeddings.get(0).getAsJsonArray();

        List<Float> vector = new ArrayList<>();
        first.forEach(v -> vector.add(v.getAsFloat()));

        return vector;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, ModelTarget target) {
        List<List<Float>> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text, target));
        }
        return vectors;
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
