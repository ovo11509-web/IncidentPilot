/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.retrieve.channel.impl;

import cn.hutool.core.collection.CollUtil;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.SearchChannelProperties;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.retrieve.RetrieverService;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannel;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchChannelType;
import com.nageoffer.ai.ragent.rag.core.retrieve.channel.SearchContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 意图定向检索通道
 * <p>
 * 基于意图识别结果，在特定知识库中进行定向检索
 * 这是最精确的检索方式，优先级最高
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentDirectedSearchChannel implements SearchChannel {

    private final RetrieverService retrieverService;
    private final SearchChannelProperties properties;
    @Qualifier("ragInnerRetrievalThreadPoolExecutor")
    private final Executor ragInnerRetrievalExecutor;

    @Override
    public String getName() {
        return "IntentDirectedSearch";
    }

    @Override
    public int getPriority() {
        return 1;  // 最高优先级
    }

    @Override
    public boolean isEnabled(SearchContext context) {
        // 检查配置是否启用
        if (!properties.getChannels().getIntentDirected().isEnabled()) {
            return false;
        }

        // 只要有意图识别结果，就启用
        return CollUtil.isNotEmpty(context.getIntents());
    }

    @Override
    public SearchChannelResult search(SearchContext context) {
        long startTime = System.currentTimeMillis();

        try {
            // 提取 KB 意图
            List<NodeScore> kbIntents = extractKbIntents(context);

            if (CollUtil.isEmpty(kbIntents)) {
                log.info("未识别出 KB 意图，跳过意图定向检索");
                return SearchChannelResult.builder()
                        .channelType(SearchChannelType.INTENT_DIRECTED)
                        .channelName(getName())
                        .chunks(List.of())
                        .confidence(0.0)
                        .latencyMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            log.info("执行意图定向检索，识别出 {} 个 KB 意图", kbIntents.size());

            // 并行检索所有意图对应的知识库
            int topKMultiplier = properties.getChannels().getIntentDirected().getTopKMultiplier();
            List<RetrievedChunk> allChunks = retrieveByIntents(
                    context.getMainQuestion(),
                    kbIntents,
                    context.getTopK() * topKMultiplier
            );

            // 计算置信度（基于意图分数）
            double confidence = kbIntents.stream()
                    .mapToDouble(NodeScore::getScore)
                    .max()
                    .orElse(0.0);

            long latency = System.currentTimeMillis() - startTime;

            log.info("意图定向检索完成，检索到 {} 个 Chunk，置信度：{}，耗时 {}ms",
                    allChunks.size(), confidence, latency);

            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.INTENT_DIRECTED)
                    .channelName(getName())
                    .chunks(allChunks)
                    .confidence(confidence)
                    .latencyMs(latency)
                    .metadata(Map.of("intentCount", kbIntents.size()))
                    .build();

        } catch (Exception e) {
            log.error("意图定向检索失败", e);
            return SearchChannelResult.builder()
                    .channelType(SearchChannelType.INTENT_DIRECTED)
                    .channelName(getName())
                    .chunks(List.of())
                    .confidence(0.0)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.INTENT_DIRECTED;
    }

    /**
     * 提取 KB 意图
     */
    private List<NodeScore> extractKbIntents(SearchContext context) {
        double minScore = properties.getChannels().getIntentDirected().getMinIntentScore();
        return context.getIntents().stream()
                .flatMap(si -> si.nodeScores().stream())
                .filter(ns -> ns.getNode() != null && ns.getNode().isKB())
                .filter(ns -> ns.getScore() >= minScore)
                .toList();
    }

    /**
     * 根据意图列表并行检索
     */
    private List<RetrievedChunk> retrieveByIntents(String question,
                                                     List<NodeScore> kbIntents,
                                                     int topK) {
        List<CompletableFuture<List<RetrievedChunk>>> futures = kbIntents.stream()
                .map(ns -> CompletableFuture.supplyAsync(() -> {
                    IntentNode node = ns.getNode();
                    return retrieverService.retrieve(
                            RetrieveRequest.builder()
                                    .collectionName(node.getCollectionName())
                                    .query(question)
                                    .topK(topK)
                                    .build()
                    );
                }, ragInnerRetrievalExecutor))
                .toList();

        // 等待所有检索完成并合并结果
        List<RetrievedChunk> allChunks = new ArrayList<>();
        futures.forEach(future -> {
            try {
                allChunks.addAll(future.join());
            } catch (Exception e) {
                log.error("意图检索失败", e);
            }
        });

        return allChunks;
    }
}
