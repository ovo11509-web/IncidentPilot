package com.nageoffer.ai.ragent.rag.rerank;

import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import com.nageoffer.ai.ragent.rag.retrieve.RetrievedChunk;

import java.util.List;

public interface RerankClient {

    String provider();

    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN, ModelTarget target);
}
