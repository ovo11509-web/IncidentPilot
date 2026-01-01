package com.nageoffer.ai.ragent.rag.model;

import com.nageoffer.ai.ragent.config.AIModelProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ModelHealthStore {

    private final AIModelProperties properties;

    private final Map<String, ModelHealth> healthById = new ConcurrentHashMap<>();

    public boolean isOpen(String id) {
        ModelHealth health = healthById.get(id);
        if (health == null) {
            return false;
        }
        return health.openUntil > System.currentTimeMillis();
    }

    public void markSuccess(String id) {
        if (id == null) {
            return;
        }
        healthById.compute(id, (k, v) -> {
            if (v == null) {
                return new ModelHealth(0, 0L);
            }
            v.consecutiveFailures = 0;
            v.openUntil = 0L;
            return v;
        });
    }

    public void markFailure(String id) {
        if (id == null) {
            return;
        }
        long now = System.currentTimeMillis();
        healthById.compute(id, (k, v) -> {
            if (v == null) {
                v = new ModelHealth(0, 0L);
            }
            v.consecutiveFailures++;
            if (v.consecutiveFailures >= properties.getSelection().getFailureThreshold()) {
                v.openUntil = now + properties.getSelection().getOpenDurationMs();
                v.consecutiveFailures = 0;
            }
            return v;
        });
    }

    private static class ModelHealth {
        private int consecutiveFailures;
        private long openUntil;

        private ModelHealth(int consecutiveFailures, long openUntil) {
            this.consecutiveFailures = consecutiveFailures;
            this.openUntil = openUntil;
        }
    }
}
