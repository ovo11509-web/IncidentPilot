package com.nageoffer.ai.ragent.service;

import com.nageoffer.ai.ragent.controller.vo.ConversationMessageVO;
import com.nageoffer.ai.ragent.controller.vo.ConversationVO;

import java.util.List;

public interface ConversationHistoryService {

    List<ConversationVO> listConversations(String userId);

    List<ConversationMessageVO> listMessages(String conversationId, String userId);
}
