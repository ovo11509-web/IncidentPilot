package com.nageoffer.ai.ragent.rag.memory;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.config.MemoryProperties;
import com.nageoffer.ai.ragent.convention.ChatMessage;
import com.nageoffer.ai.ragent.convention.ChatRequest;
import com.nageoffer.ai.ragent.dao.entity.ConversationMessageDO;
import com.nageoffer.ai.ragent.dao.entity.ConversationSummaryDO;
import com.nageoffer.ai.ragent.rag.chat.LLMService;
import com.nageoffer.ai.ragent.service.ConversationGroupService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.nageoffer.ai.ragent.constant.RAGEnterpriseConstant.CONVERSATION_SUMMARY_PROMPT;

@Slf4j
@Service
public class MySQLConversationMemorySummaryService implements ConversationMemorySummaryService {

    private static final String SUMMARY_PREFIX = "对话摘要：";
    private static final String SUMMARY_LOCK_PREFIX = "ragent:memory:summary:lock:";
    private static final Duration SUMMARY_LOCK_TTL = Duration.ofMinutes(5);

    private final ConversationGroupService conversationGroupService;
    private final MemoryProperties memoryProperties;
    private final LLMService llmService;
    private final Executor memorySummaryExecutor;
    private final ConversationMemoryStore memoryStore;
    private final RedissonClient redissonClient;

    public MySQLConversationMemorySummaryService(ConversationGroupService conversationGroupService,
                                                 MemoryProperties memoryProperties,
                                                 LLMService llmService,
                                                 @Qualifier("memorySummaryThreadPoolExecutor") Executor memorySummaryExecutor,
                                                 ConversationMemoryStore memoryStore,
                                                 RedissonClient redissonClient) {
        this.conversationGroupService = conversationGroupService;
        this.memoryProperties = memoryProperties;
        this.llmService = llmService;
        this.memorySummaryExecutor = memorySummaryExecutor;
        this.memoryStore = memoryStore;
        this.redissonClient = redissonClient;
    }

    @Override
    public void compressIfNeeded(String conversationId, String userId, ChatMessage message) {
        if (!memoryProperties.isSummaryEnabled()) {
            return;
        }
        if (message.getRole() != ChatMessage.Role.ASSISTANT) {
            return;
        }
        CompletableFuture.runAsync(() -> doCompressIfNeeded(conversationId, userId), memorySummaryExecutor)
                .exceptionally(ex -> {
                    log.error("对话记忆摘要异步任务失败", ex);
                    return null;
                });
    }

    @Override
    public ChatMessage loadLatestSummary(String conversationId, String userId) {
        ConversationSummaryDO summary = loadLatestSummaryRecord(conversationId, userId);
        return toChatMessage(summary);
    }

    @Override
    public ChatMessage decorateIfNeeded(ChatMessage summary) {
        if (summary == null || StrUtil.isBlank(summary.getContent())) {
            return summary;
        }
        String content = summary.getContent().trim();
        if (content.startsWith(SUMMARY_PREFIX) || content.startsWith("摘要：")) {
            return summary;
        }
        return ChatMessage.system(SUMMARY_PREFIX + content);
    }

    private void doCompressIfNeeded(String conversationId, String userId) {
        long startTime = System.currentTimeMillis();
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(userId)) {
            return;
        }
        int triggerTurns = memoryProperties.getSummaryTriggerTurns();
        int maxTurns = requirePositiveMaxTurns();
        if (triggerTurns <= 0) {
            return;
        }
        String lockKey = SUMMARY_LOCK_PREFIX + buildLockKey(conversationId, userId);
        RLock lock = redissonClient.getLock(lockKey);
        if (!tryLock(lock)) {
            return;
        }
        try {
            long total = conversationGroupService.countUserMessages(conversationId, userId);
            if (total < triggerTurns) {
                return;
            }
            if (total <= maxTurns) {
                return;
            }
            ConversationSummaryDO latestSummary = loadLatestSummaryRecord(conversationId, userId);
            List<ConversationMessageDO> latestUserTurns = conversationGroupService.listLatestUserOnlyMessages(
                    conversationId,
                    userId,
                    maxTurns
            );
            if (latestUserTurns.isEmpty()) {
                return;
            }
            Date cutoff = resolveCutoff(latestUserTurns);
            if (cutoff == null) {
                return;
            }
            Date after = resolveSummaryStart(latestSummary);
            if (after != null && !after.before(cutoff)) {
                return;
            }
            List<ConversationMessageDO> toSummarize = conversationGroupService.listMessagesBetween(
                    conversationId,
                    userId,
                    after,
                    cutoff
            );
            if (toSummarize == null || toSummarize.isEmpty()) {
                return;
            }
            Date summaryTime = resolveSummaryTime(toSummarize);
            if (summaryTime == null) {
                return;
            }
            String existingSummary = latestSummary == null ? "" : latestSummary.getContent();
            String summary = summarizeMessages(toSummarize, existingSummary);
            if (StrUtil.isBlank(summary)) {
                return;
            }
            upsertSummary(latestSummary, conversationId, userId, summary, summaryTime);
            log.info("摘要成功 - conversationId: {}, 消息数: {}, 耗时: {}ms",
                    conversationId, toSummarize.size(), System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("摘要失败 - conversationId: {}, userId: {}", conversationId, userId, e);
        } finally {
            unlockIfOwner(lock);
        }
    }

    private boolean tryLock(RLock lock) {
        try {
            return lock.tryLock(0, SUMMARY_LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void unlockIfOwner(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private String summarizeMessages(List<ConversationMessageDO> messages, String existingSummary) {
        String content = buildSummaryContent(messages);
        if (StrUtil.isBlank(content)) {
            return "";
        }
        List<ChatMessage> summaryMessages = new ArrayList<>();
        summaryMessages.add(ChatMessage.system(CONVERSATION_SUMMARY_PROMPT));
        if (StrUtil.isNotBlank(existingSummary)) {
            summaryMessages.add(ChatMessage.user("已有摘要（仅供参考，需在此基础上更新，禁止作为事实来源）：\n"
                    + existingSummary.trim()));
        }
        summaryMessages.add(ChatMessage.user("新对话内容：\n" + content));
        ChatRequest request = ChatRequest.builder()
                .messages(summaryMessages)
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();
        try {
            String result = llmService.chat(request);
            return result == null ? "" : result.trim();
        } catch (Exception e) {
            log.error("对话记忆摘要生成失败, conversationId相关消息数: {}", messages.size(), e);
            return "";
        }
    }

    private String buildSummaryContent(List<ConversationMessageDO> messages) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessageDO item : messages) {
            if (item == null || StrUtil.isBlank(item.getContent())) {
                continue;
            }
            if (isHistoryRole(item.getRole())) {
                sb.append(toRoleLabel(item.getRole()))
                        .append(item.getContent().trim())
                        .append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String toRoleLabel(String role) {
        if (StrUtil.isBlank(role)) {
            return "";
        }
        return switch (role.trim().toLowerCase()) {
            case "user" -> "用户：";
            case "assistant" -> "助手：";
            case "system" -> "系统：";
            default -> "";
        };
    }

    private ConversationSummaryDO loadLatestSummaryRecord(String conversationId, String userId) {
        return conversationGroupService.findLatestSummary(conversationId, userId);
    }

    private ChatMessage toChatMessage(ConversationSummaryDO record) {
        if (record == null || StrUtil.isBlank(record.getContent())) {
            return null;
        }
        return new ChatMessage(ChatMessage.Role.SYSTEM, record.getContent());
    }

    private Date resolveSummaryStart(ConversationSummaryDO summary) {
        if (summary == null) {
            return null;
        }
        Date after = summary.getUpdateTime();
        return after == null ? summary.getCreateTime() : after;
    }

    private Date resolveCutoff(List<ConversationMessageDO> latestUserTurns) {
        for (int i = latestUserTurns.size() - 1; i >= 0; i--) {
            ConversationMessageDO item = latestUserTurns.get(i);
            if (item != null && item.getCreateTime() != null) {
                return item.getCreateTime();
            }
        }
        return null;
    }

    private Date resolveSummaryTime(List<ConversationMessageDO> toSummarize) {
        return toSummarize.stream()
                .map(ConversationMessageDO::getCreateTime)
                .filter(Objects::nonNull)
                .max(Date::compareTo)
                .orElse(null);
    }

    private void upsertSummary(ConversationSummaryDO latestSummary,
                               String conversationId,
                               String userId,
                               String content,
                               Date summaryTime) {
        ConversationSummaryDO summaryRecord = latestSummary;
        if (summaryRecord == null) {
            summaryRecord = ConversationSummaryDO.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .content(content)
                    .createTime(summaryTime)
                    .updateTime(summaryTime)
                    .build();
        } else {
            summaryRecord.setContent(content);
            summaryRecord.setUpdateTime(summaryTime);
        }
        conversationGroupService.upsertSummary(summaryRecord);
        memoryStore.refreshCache(conversationId, userId);
    }

    private int requirePositiveMaxTurns() {
        int maxTurns = memoryProperties.getMaxTurns();
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("rag.memory.max-turns must be > 0");
        }
        return maxTurns;
    }

    private boolean isHistoryRole(String role) {
        if (StrUtil.isBlank(role)) {
            return false;
        }
        String normalized = role.trim().toLowerCase();
        return "user".equals(normalized) || "assistant".equals(normalized);
    }

    private String buildLockKey(String conversationId, String userId) {
        return userId.trim() + ":" + conversationId.trim();
    }
}
