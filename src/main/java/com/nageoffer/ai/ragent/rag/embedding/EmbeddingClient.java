package com.nageoffer.ai.ragent.rag.embedding;

import com.nageoffer.ai.ragent.rag.model.ModelTarget;

import java.util.List;

public interface EmbeddingClient {

    String provider();

    List<Float> embed(String text, ModelTarget target);

    List<List<Float>> embedBatch(List<String> texts, ModelTarget target);
}
