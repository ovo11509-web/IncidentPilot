package com.nageoffer.ai.ragent.rag.memory;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.gson.Gson;
import com.nageoffer.ai.ragent.config.MemoryProperties;
import com.nageoffer.ai.ragent.convention.ChatMessage;
import com.nageoffer.ai.ragent.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.dao.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RedisConversationMemoryService implements ConversationMemoryService {

    private static final String KEY_PREFIX = "ragent:memory:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ConversationMessageMapper messageMapper;
    private final MemoryProperties memoryProperties;
    private final Gson gson = new Gson();

    @Override
    public List<ChatMessage> load(String conversationId, String userId, int maxMessages) {
        String key = buildKey(conversationId, userId);
        if (key == null) {
            return List.of();
        }
        long size = Math.max(maxMessages, 0);
        if (size == 0) {
            return List.of();
        }
        List<String> raw = stringRedisTemplate.opsForList().range(key, -size, -1);
        if (raw != null && !raw.isEmpty()) {
            List<ChatMessage> cached = parseMessages(raw);
            if (!cached.isEmpty()) {
                return cached;
            }
        }

        List<ConversationMessageDO> dbMessages = messageMapper.selectList(
                Wrappers.lambdaQuery(ConversationMessageDO.class)
                        .eq(ConversationMessageDO::getConversationId, conversationId)
                        .eq(ConversationMessageDO::getUserId, normalizeUserId(userId))
                        .orderByDesc(ConversationMessageDO::getCreateTime)
                        .last("limit " + size)
        );
        if (dbMessages == null || dbMessages.isEmpty()) {
            return List.of();
        }
        dbMessages.sort(Comparator.comparing(ConversationMessageDO::getCreateTime));
        List<ChatMessage> result = dbMessages.stream()
                .map(this::toChatMessage)
                .filter(item -> item != null && StrUtil.isNotBlank(item.getContent()))
                .collect(Collectors.toList());
        if (!result.isEmpty()) {
            List<String> payloads = result.stream()
                    .map(gson::toJson)
                    .toList();
            stringRedisTemplate.opsForList().rightPushAll(key, payloads);
            applyExpire(key);
        }
        return result;
    }

    @Override
    public void append(String conversationId, String userId, ChatMessage message) {
        String key = buildKey(conversationId, userId);
        if (key == null || message == null) {
            return;
        }
        persistToDb(conversationId, userId, message);
        String payload = gson.toJson(message);
        stringRedisTemplate.opsForList().rightPush(key, payload);
        applyExpire(key);
    }

    private String buildKey(String conversationId, String userId) {
        if (StrUtil.isBlank(conversationId)) {
            return null;
        }
        String safeUserId = normalizeUserId(userId);
        return KEY_PREFIX + safeUserId + ":" + conversationId.trim();
    }

    private String normalizeUserId(String userId) {
        return StrUtil.isBlank(userId) ? "anon" : userId.trim();
    }

    private List<ChatMessage> parseMessages(List<String> raw) {
        List<ChatMessage> result = new ArrayList<>();
        for (String item : raw) {
            if (StrUtil.isBlank(item)) {
                continue;
            }
            result.add(gson.fromJson(item, ChatMessage.class));
        }
        return result;
    }

    private void applyExpire(String key) {
        int ttlMinutes = memoryProperties.getTtlMinutes();
        if (ttlMinutes > 0) {
            stringRedisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));
        }
    }

    private void persistToDb(String conversationId, String userId, ChatMessage message) {
        ConversationMessageDO record = ConversationMessageDO.builder()
                .conversationId(conversationId)
                .userId(normalizeUserId(userId))
                .role(message.getRole() == null ? null : message.getRole().name().toLowerCase())
                .content(message.getContent())
                .build();
        messageMapper.insert(record);
    }

    private ChatMessage toChatMessage(ConversationMessageDO record) {
        if (record == null || StrUtil.isBlank(record.getContent())) {
            return null;
        }
        ChatMessage.Role role = parseRole(record.getRole());
        return new ChatMessage(role, record.getContent());
    }

    private ChatMessage.Role parseRole(String role) {
        if (StrUtil.isBlank(role)) {
            return ChatMessage.Role.USER;
        }
        String normalized = role.trim().toUpperCase();
        try {
            return ChatMessage.Role.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return ChatMessage.Role.USER;
        }
    }
}
