package com.nageoffer.ai.ragent.rag.memory;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.convention.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultConversationMemoryService implements ConversationMemoryService {

    private final ConversationMemoryStore memoryStore;
    private final ConversationMemorySummaryService summaryService;

    public DefaultConversationMemoryService(ConversationMemoryStore memoryStore,
                                            ConversationMemorySummaryService summaryService) {
        this.memoryStore = memoryStore;
        this.summaryService = summaryService;
    }

    @Override
    public List<ChatMessage> load(String conversationId, String userId) {
        if (StrUtil.isBlank(conversationId)) {
            return List.of();
        }
        ChatMessage summary = summaryService.loadLatestSummary(conversationId, userId);
        List<ChatMessage> history = memoryStore.loadHistory(conversationId, userId);
        return attachSummary(summary, history);
    }

    @Override
    public void append(String conversationId, String userId, ChatMessage message) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return;
        }
        memoryStore.append(conversationId, userId, message);
        summaryService.compressIfNeeded(conversationId, userId, message);
    }

    private List<ChatMessage> attachSummary(ChatMessage summary, List<ChatMessage> messages) {
        if (summary == null) {
            return messages;
        }
        List<ChatMessage> result = new ArrayList<>();
        result.add(summaryService.decorateIfNeeded(summary));
        result.addAll(messages);
        return result;
    }
}
