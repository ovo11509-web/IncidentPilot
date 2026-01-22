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

package com.nageoffer.ai.ragent.ingestion.strategy.chunker;

import com.nageoffer.ai.ragent.ingestion.domain.context.DocumentChunk;
import com.nageoffer.ai.ragent.ingestion.domain.enums.ChunkStrategy;
import com.nageoffer.ai.ragent.ingestion.domain.settings.ChunkerSettings;

import java.util.List;

/**
 * 文档分块策略接口
 * 用于将长文本按照特定的策略划分为多个较小的文档块（DocumentChunk）
 */
public interface ChunkingStrategy {

    /**
     * 获取当前分块策略的类型
     *
     * @return 分块策略类型枚举 {@link ChunkStrategy}
     */
    ChunkStrategy getStrategyType();

    /**
     * 对文本进行分块处理
     *
     * @param text     待分块的原始文本内容
     * @param settings 分块配置参数，如块大小、重叠度等 {@link ChunkerSettings}
     * @return 分块后的文档块列表 {@link List<DocumentChunk>}
     */
    List<DocumentChunk> chunk(String text, ChunkerSettings settings);
}
