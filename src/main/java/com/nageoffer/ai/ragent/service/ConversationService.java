package com.nageoffer.ai.ragent.service;

import com.nageoffer.ai.ragent.controller.request.ConversationCreateRequest;
import com.nageoffer.ai.ragent.controller.request.ConversationUpdateRequest;
import com.nageoffer.ai.ragent.controller.vo.ConversationVO;

public interface ConversationService {

    ConversationVO create(String userId, ConversationCreateRequest request);

    void rename(String conversationId, String userId, ConversationUpdateRequest request);

    void delete(String conversationId, String userId);
}
