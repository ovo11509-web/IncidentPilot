package com.nageoffer.ai.ragent.service;

import com.nageoffer.ai.ragent.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.dao.entity.ConversationMessageDO;

import java.util.List;

public interface ConversationGroupService {

    List<ConversationMessageDO> listLatestUserMessages(String conversationId, String userId, int limit);

    List<ConversationMessageDO> listUserMessagesAsc(String conversationId, String userId, int limit);

    long countUserMessages(String conversationId, String userId);

    List<ConversationMessageDO> listEarliestUserMessages(String conversationId, String userId, int limit);

    ConversationMessageDO findLatestSummary(String conversationId, String userId);

    void saveMessage(ConversationMessageDO record);

    void upsertSummary(ConversationMessageDO record);

    ConversationDO findConversation(String conversationId, String userId);

    void upsertConversation(ConversationDO record);
}
