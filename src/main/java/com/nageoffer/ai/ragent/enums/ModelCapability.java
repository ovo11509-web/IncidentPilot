package com.nageoffer.ai.ragent.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelCapability {

    CHAT("Chat"),

    EMBEDDING("Embedding"),

    RERANK("Rerank");

    private final String displayName;
}
