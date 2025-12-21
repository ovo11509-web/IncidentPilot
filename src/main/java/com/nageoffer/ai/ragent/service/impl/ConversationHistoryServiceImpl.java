package com.nageoffer.ai.ragent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.controller.vo.ConversationMessageVO;
import com.nageoffer.ai.ragent.controller.vo.ConversationVO;
import com.nageoffer.ai.ragent.dao.entity.ConversationDO;
import com.nageoffer.ai.ragent.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.dao.mapper.ConversationMapper;
import com.nageoffer.ai.ragent.dao.mapper.ConversationMessageMapper;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.service.ConversationHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationHistoryServiceImpl implements ConversationHistoryService {

    private final ConversationMessageMapper messageMapper;
    private final ConversationMapper conversationMapper;

    @Override
    public List<ConversationVO> listConversations(String userId) {
        if (StrUtil.isBlank(userId)) {
            return List.of();
        }
        List<ConversationDO> records = conversationMapper.selectList(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
                        .orderByDesc(ConversationDO::getLastTime)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(item -> ConversationVO.builder()
                        .conversationId(item.getConversationId())
                        .title(item.getTitle())
                        .lastTime(item.getLastTime())
                        .createTime(item.getCreateTime())
                        .updateTime(item.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<ConversationMessageVO> listMessages(String conversationId, String userId, boolean includeSummary) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return List.of();
        }
        ConversationDO conversation = conversationMapper.selectOne(
                Wrappers.lambdaQuery(ConversationDO.class)
                        .eq(ConversationDO::getConversationId, conversationId)
                        .eq(ConversationDO::getUserId, userId)
                        .eq(ConversationDO::getDeleted, 0)
        );
        if (conversation == null) {
            throw new ClientException("会话不存在");
        }
        List<ConversationMessageDO> records = messageMapper.selectList(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, conversationId)
                        .eq(ConversationMessageDO::getUserId, userId)
                        .eq(!includeSummary, ConversationMessageDO::getIsSummary, 0)
                        .eq(ConversationMessageDO::getDeleted, 0)
                        .orderByAsc(ConversationMessageDO::getCreateTime)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<ConversationMessageVO> result = new ArrayList<>();
        for (ConversationMessageDO record : records) {
            ConversationMessageVO vo = ConversationMessageVO.builder()
                    .id(record.getId())
                    .conversationId(record.getConversationId())
                    .role(record.getRole())
                    .content(record.getContent())
                    .isSummary(record.getIsSummary())
                    .createTime(record.getCreateTime())
                    .build();
            result.add(vo);
        }
        return result;
    }
}
