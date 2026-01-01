package com.nageoffer.ai.ragent.rag.chat;

import com.nageoffer.ai.ragent.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import com.nageoffer.ai.ragent.rag.model.ModelHealthStore;
import com.nageoffer.ai.ragent.rag.model.ModelSelector;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
public class RoutingLLMService implements LLMService {

    private final ModelSelector selector;
    private final ModelHealthStore healthStore;
    private final Map<String, ChatClient> clientsByProvider;

    public RoutingLLMService(ModelSelector selector, ModelHealthStore healthStore, List<ChatClient> clients) {
        this.selector = selector;
        this.healthStore = healthStore;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(ChatClient::provider, Function.identity()));
    }

    @Override
    public String chat(ChatRequest request) {
        List<ModelTarget> targets = selector.selectChatCandidates();
        if (targets.isEmpty()) {
            throw new RemoteException("No chat model candidates available");
        }

        Throwable last = null;
        for (ModelTarget target : targets) {
            ChatClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) {
                log.warn("Chat provider client missing: provider={}, modelId={}",
                        target.candidate().getProvider(), target.id());
                continue;
            }
            try {
                String response = client.chat(request, target);
                healthStore.markSuccess(target.id());
                return response;
            } catch (Exception e) {
                last = e;
                healthStore.markFailure(target.id());
                log.warn("Chat model failed, fallback to next. modelId={}, provider={}", target.id(), target.candidate().getProvider(), e);
            }
        }
        throw new RemoteException(
                "All chat model candidates failed: " + (last == null ? "unknown" : last.getMessage()),
                last,
                com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode.REMOTE_ERROR
        );
    }

    @Override
    public StreamHandle streamChat(ChatRequest request, StreamCallback callback) {
        List<ModelTarget> targets = selector.selectChatCandidates();
        if (targets.isEmpty()) {
            throw new RemoteException("No chat model candidates available");
        }

        Throwable last = null;
        for (ModelTarget target : targets) {
            ChatClient client = clientsByProvider.get(target.candidate().getProvider());
            if (client == null) {
                log.warn("Chat provider client missing: provider={}, modelId={}",
                        target.candidate().getProvider(), target.id());
                continue;
            }
            TrackingCallback tracking = new TrackingCallback(callback);
            try {
                StreamHandle handle = client.streamChat(request, tracking, target);
                if (tracking.hasError() && !tracking.hasContent()) {
                    healthStore.markFailure(target.id());
                    last = tracking.getError();
                    continue;
                }
                if (tracking.hasError()) {
                    healthStore.markFailure(target.id());
                    tracking.forwardError();
                } else {
                    healthStore.markSuccess(target.id());
                }
                return handle;
            } catch (Exception e) {
                last = e;
                healthStore.markFailure(target.id());
                log.warn("Chat streaming failed before content, fallback to next. modelId={}, provider={}",
                        target.id(), target.candidate().getProvider(), e);
            }
        }
        throw new RemoteException(
                "All chat model candidates failed: " + (last == null ? "unknown" : last.getMessage()),
                last,
                com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode.REMOTE_ERROR
        );
    }

    private static class TrackingCallback implements StreamCallback {
        private final StreamCallback delegate;
        private final AtomicBoolean hasContent = new AtomicBoolean(false);
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        private TrackingCallback(StreamCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onContent(String content) {
            hasContent.set(true);
            delegate.onContent(content);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public void onError(Throwable ex) {
            error.set(ex);
        }

        public boolean hasContent() {
            return hasContent.get();
        }

        public boolean hasError() {
            return error.get() != null;
        }

        public Throwable getError() {
            return error.get();
        }

        public void forwardError() {
            Throwable ex = error.get();
            if (ex != null) {
                delegate.onError(ex);
            }
        }
    }
}
