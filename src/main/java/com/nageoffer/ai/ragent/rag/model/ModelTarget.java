package com.nageoffer.ai.ragent.rag.model;

import com.nageoffer.ai.ragent.config.AIModelProperties;

public record ModelTarget(
        String id,
        AIModelProperties.ModelCandidate candidate,
        AIModelProperties.ProviderConfig provider
) {
}
