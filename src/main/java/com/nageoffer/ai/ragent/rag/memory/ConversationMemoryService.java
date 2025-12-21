package com.nageoffer.ai.ragent.rag.memory;

import com.nageoffer.ai.ragent.convention.ChatMessage;

import java.util.List;

public interface ConversationMemoryService {

    List<ChatMessage> load(String conversationId, String userId, int maxMessages);

    void append(String conversationId, String userId, ChatMessage message);
}
