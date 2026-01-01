package com.nageoffer.ai.ragent.rag.rerank;

import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import com.nageoffer.ai.ragent.rag.model.ModelHealthStore;
import com.nageoffer.ai.ragent.rag.model.ModelSelector;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import com.nageoffer.ai.ragent.rag.retrieve.RetrievedChunk;
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
public class RoutingRerankService implements RerankService {

    private final ModelSelector selector;
    private final ModelHealthStore healthStore;
    private final Map<String, RerankClient> clientsByProvider;

    public RoutingRerankService(ModelSelector selector, ModelHealthStore healthStore, List<RerankClient> clients) {
        this.selector = selector;
        this.healthStore = healthStore;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(RerankClient::provider, Function.identity()));
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        List<ModelTarget> targets = selector.selectRerankCandidates();
        if (targets.isEmpty()) {
            throw new RemoteException("No rerank model candidates available");
        }
        Exception last = null;
        for (ModelTarget target : targets) {
            RerankClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) {
                log.warn("Rerank provider client missing: provider={}, modelId={}",
                        target.candidate().getProvider(), target.id());
                continue;
            }
            try {
                List<RetrievedChunk> reranked = client.rerank(query, candidates, topN, target);
                healthStore.markSuccess(target.id());
                return reranked;
            } catch (Exception e) {
                last = e;
                healthStore.markFailure(target.id());
                log.warn("Rerank model failed, fallback to next. modelId={}, provider={}",
                        target.id(), target.candidate().getProvider(), e);
            }
        }
        throw new RemoteException(
                "All rerank model candidates failed: " + (last == null ? "unknown" : last.getMessage()),
                last,
                com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode.REMOTE_ERROR
        );
    }
}
