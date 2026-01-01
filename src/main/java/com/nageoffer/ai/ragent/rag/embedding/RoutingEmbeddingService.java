package com.nageoffer.ai.ragent.rag.embedding;

import com.nageoffer.ai.ragent.enums.ModelCapability;
import com.nageoffer.ai.ragent.rag.model.ModelSelector;
import com.nageoffer.ai.ragent.rag.model.ModelRoutingExecutor;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Primary
public class RoutingEmbeddingService implements EmbeddingService {

    private final ModelSelector selector;
    private final ModelRoutingExecutor executor;
    private final Map<String, EmbeddingClient> clientsByProvider;

    public RoutingEmbeddingService(ModelSelector selector, ModelRoutingExecutor executor, List<EmbeddingClient> clients) {
        this.selector = selector;
        this.executor = executor;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(EmbeddingClient::provider, Function.identity()));
    }

    @Override
    public List<Float> embed(String text) {
        return executor.executeWithFallback(
                ModelCapability.EMBEDDING,
                selector.selectEmbeddingCandidates(),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.embed(text, target)
        );
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        return executor.executeWithFallback(
                ModelCapability.EMBEDDING,
                selector.selectEmbeddingCandidates(),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.embedBatch(texts, target)
        );
    }

    @Override
    public int dimension() {
        ModelTarget target = selector.selectDefaultEmbedding();
        if (target == null || target.candidate().getDimension() == null) {
            return 0;
        }
        return target.candidate().getDimension();
    }
}
