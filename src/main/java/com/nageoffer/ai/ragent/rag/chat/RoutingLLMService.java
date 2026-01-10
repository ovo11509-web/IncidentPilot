package com.nageoffer.ai.ragent.rag.chat;

import com.nageoffer.ai.ragent.convention.ChatRequest;
import com.nageoffer.ai.ragent.enums.ModelCapability;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import com.nageoffer.ai.ragent.rag.model.ModelHealthStore;
import com.nageoffer.ai.ragent.rag.model.ModelRoutingExecutor;
import com.nageoffer.ai.ragent.rag.model.ModelSelector;
import com.nageoffer.ai.ragent.rag.model.ModelTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Primary
public class RoutingLLMService implements LLMService {

    private final ModelSelector selector;
    private final ModelHealthStore healthStore;
    private final ModelRoutingExecutor executor;
    private final Map<String, ChatClient> clientsByProvider;

    public RoutingLLMService(
            ModelSelector selector,
            ModelHealthStore healthStore,
            ModelRoutingExecutor executor,
            List<ChatClient> clients) {
        this.selector = selector;
        this.healthStore = healthStore;
        this.executor = executor;
        this.clientsByProvider = clients.stream()
                .collect(Collectors.toMap(ChatClient::provider, Function.identity()));
    }

    @Override
    public String chat(ChatRequest request) {
        return executor.executeWithFallback(
                ModelCapability.CHAT,
                selector.selectChatCandidates(),
                target -> clientsByProvider.get(target.candidate().getProvider()),
                (client, target) -> client.chat(request, target)
        );
    }

    @Override
    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        List<ModelTarget> targets = selector.selectChatCandidates();
        if (targets.isEmpty()) {
            throw new RemoteException("没有可用的Chat模型候选者");
        }

        String label = ModelCapability.CHAT.getDisplayName();
        Throwable lastError = null;

        for (ModelTarget target : targets) {
            ChatClient client = resolveClient(target, label);
            if (client == null) {
                continue;
            }

            FirstPacketAwaiter awaiter = new FirstPacketAwaiter();
            StreamCallback wrapper = new StreamCallback() {
                @Override
                public void onContent(String content) {
                    awaiter.markContent();
                    callback.onContent(content);
                }

                @Override
                public void onComplete() {
                    awaiter.markComplete();
                    callback.onComplete();
                }

                @Override
                public void onError(Throwable t) {
                    awaiter.markError(t);
                    callback.onError(t);
                }
            };

            StreamCancellationHandle handle = client.streamChat(request, wrapper, target);
            FirstPacketAwaiter.Result result;
            try {
                result = awaiter.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handle.cancel();
                throw new RemoteException("流式请求被中断", e, BaseErrorCode.REMOTE_ERROR);
            }

            // 判断结果
            if (result.isSuccess()) {
                healthStore.markSuccess(target.id());
                return handle;
            }

            // 失败处理
            healthStore.markFailure(target.id());
            handle.cancel();

            switch (result.getType()) {
                case ERROR:
                    lastError = result.getError();
                    log.warn("{} 流式请求失败，切换下一个模型。modelId：{}，provider：{}",
                            label, target.id(), target.candidate().getProvider(), lastError);
                    break;
                case TIMEOUT:
                    lastError = new RemoteException("流式首包超时", BaseErrorCode.REMOTE_ERROR);
                    log.warn("{} 流式请求超时，切换下一个模型。modelId：{}", label, target.id());
                    break;
                case NO_CONTENT:
                    lastError = new RemoteException("流式请求未返回内容", BaseErrorCode.REMOTE_ERROR);
                    log.warn("{} 流式请求无内容完成，切换下一个模型。modelId：{}", label, target.id());
                    break;
            }
        }

        throw new RemoteException(
                "所有Chat模型候选者都失败了: " + (lastError == null ? "未知" : lastError.getMessage()),
                lastError,
                BaseErrorCode.REMOTE_ERROR
        );
    }

    private ChatClient resolveClient(ModelTarget target, String label) {
        ChatClient client = clientsByProvider.get(target.candidate().getProvider());
        if (client == null) {
            log.warn("{} 提供商客户端缺失: provider：{}，modelId：{}",
                    label, target.candidate().getProvider(), target.id());
        }
        return client;
    }
}
