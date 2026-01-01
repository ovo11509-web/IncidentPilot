package com.nageoffer.ai.ragent.rag.model;

import com.nageoffer.ai.ragent.enums.ModelCapability;
import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelRoutingExecutor {

    private final ModelHealthStore healthStore;

    public <C, T> T executeWithFallback(
            ModelCapability capability,
            List<ModelTarget> targets,
            Function<ModelTarget, C> clientResolver,
            ModelCaller<C, T> caller
    ) {
        String label = capability.getDisplayName();
        if (targets == null || targets.isEmpty()) {
            throw new RemoteException("No " + label + " model candidates available");
        }

        Throwable last = null;
        for (ModelTarget target : targets) {
            C client = clientResolver.apply(target);
            if (client == null) {
                log.warn("{} provider client missing: provider={}, modelId={}", label, target.candidate().getProvider(), target.id());
                continue;
            }

            try {
                T response = caller.call(client, target);
                healthStore.markSuccess(target.id());
                return response;
            } catch (Exception e) {
                last = e;
                healthStore.markFailure(target.id());
                log.warn("{} model failed, fallback to next. modelId={}, provider={}", label, target.id(), target.candidate().getProvider(), e);
            }
        }

        throw new RemoteException(
                "All " + label + " model candidates failed: " + (last == null ? "unknown" : last.getMessage()),
                last,
                BaseErrorCode.REMOTE_ERROR
        );
    }
}
