package com.nageoffer.ai.ragent.service;

import com.nageoffer.ai.ragent.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.service.bo.ConversationMessageBO;
import com.nageoffer.ai.ragent.service.bo.ConversationSummaryBO;

import java.util.List;

public interface ConversationMessageService {

    /**
     * 新增对话消息
     *
     * @param conversationMessage 消息内容
     */
    void addMessage(ConversationMessageBO conversationMessage);

    /**
     * 获取最新的对话消息列表
     *
     * @param conversationId 对话ID
     * @param userId         用户ID
     * @param limit          限制数量
     * @return 对话消息列表
     */
    List<ConversationMessageDO> listLatestMessages(String conversationId, String userId, Integer limit);

    /**
     * 添加对话摘要
     *
     * @param conversationSummary 对话摘要内容
     */
    void addMessageSummary(ConversationSummaryBO conversationSummary);
}
