package com.nageoffer.ai.ragent.rag.embedding;

import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import com.nageoffer.ai.ragent.rag.model.ModelHealthStore;
import com.nageoffer.ai.ragent.rag.model.ModelSelector;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
public class RoutingEmbeddingService implements EmbeddingService {

    private final ModelSelector selector;
    private final ModelHealthStore healthStore;
    private final Map<String, EmbeddingClient> clientsByProvider;

    public RoutingEmbeddingService(ModelSelector selector, ModelHealthStore healthStore, List<EmbeddingClient> clients) {
        this.selector = selector;
        this.healthStore = healthStore;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(EmbeddingClient::provider, Function.identity()));
    }

    @Override
    public List<Float> embed(String text) {
        List<ModelTarget> targets = selector.selectEmbeddingCandidates();
        if (targets.isEmpty()) {
            throw new RemoteException("No embedding model candidates available");
        }
        Exception last = null;
        for (ModelTarget target : targets) {
            EmbeddingClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) {
                log.warn("Embedding provider client missing: provider={}, modelId={}",
                        target.candidate().getProvider(), target.id());
                continue;
            }
            try {
                List<Float> vector = client.embed(text, target);
                healthStore.markSuccess(target.id());
                return vector;
            } catch (Exception e) {
                last = e;
                healthStore.markFailure(target.id());
                log.warn("Embedding model failed, fallback to next. modelId={}, provider={}",
                        target.id(), target.candidate().getProvider(), e);
            }
        }
        throw new RemoteException(
                "All embedding model candidates failed: " + (last == null ? "unknown" : last.getMessage()),
                last,
                com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode.REMOTE_ERROR
        );
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        List<ModelTarget> targets = selector.selectEmbeddingCandidates();
        if (targets.isEmpty()) {
            throw new RemoteException("No embedding model candidates available");
        }
        Exception last = null;
        for (ModelTarget target : targets) {
            EmbeddingClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) {
                log.warn("Embedding provider client missing: provider={}, modelId={}",
                        target.candidate().getProvider(), target.id());
                continue;
            }
            try {
                List<List<Float>> vectors = client.embedBatch(texts, target);
                healthStore.markSuccess(target.id());
                return vectors;
            } catch (Exception e) {
                last = e;
                healthStore.markFailure(target.id());
                log.warn("Embedding model batch failed, fallback to next. modelId={}, provider={}",
                        target.id(), target.candidate().getProvider(), e);
            }
        }
        throw new RemoteException(
                "All embedding model candidates failed: " + (last == null ? "unknown" : last.getMessage()),
                last,
                com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode.REMOTE_ERROR
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
