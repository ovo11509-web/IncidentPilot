package com.nageoffer.ai.ragent.rag.memory;

import com.nageoffer.ai.ragent.convention.ChatMessage;

public interface ConversationMemorySummaryService {

    void compressIfNeeded(String conversationId, String userId, ChatMessage message);

    ChatMessage loadLatestSummary(String conversationId, String userId);

    ChatMessage decorateIfNeeded(ChatMessage summary);
}
