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

package com.nageoffer.ai.ragent.core.chunk.strategy;

import cn.hutool.core.util.IdUtil;
import com.nageoffer.ai.ragent.core.chunk.ChunkingMode;
import com.nageoffer.ai.ragent.core.chunk.ChunkingOptions;
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.core.chunk.ChunkingStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定大小分块器
 * 按照指定的块大小和重叠大小进行文本切分，并在标点符号处优化边界
 */
@Component
public class FixedSizeTextChunker implements ChunkingStrategy {

    @Override
    public ChunkingMode getType() {
        return ChunkingMode.FIXED_SIZE;
    }

    @Override
    public List<VectorChunk> chunk(String text, ChunkingOptions config) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        int chunkSize = config.getChunkSize();
        int overlap = config.getOverlapSize();
        int len = text.length();

        List<VectorChunk> chunks = new ArrayList<>();
        int index = 0;
        int start = 0;

        while (start < len) {
            int targetEnd = Math.min(start + chunkSize, len);
            int end = adjustToBoundary(text, start, targetEnd);
            if (end <= start) {
                end = targetEnd;
            }

            String content = text.substring(start, end).trim();
            if (StringUtils.hasText(content)) {
                chunks.add(VectorChunk.builder()
                        .chunkId(IdUtil.getSnowflakeNextIdStr())
                        .index(index++)
                        .content(content)
                        .build());
            }

            if (end >= len) {
                break;
            }

            int nextStart = end - Math.max(0, overlap);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    private int adjustToBoundary(String text, int start, int targetEnd) {
        int maxLookback = Math.min(200, targetEnd - start);
        for (int i = 0; i <= maxLookback; i++) {
            int pos = targetEnd - i - 1;
            if (pos <= start) {
                break;
            }
            char c = text.charAt(pos);
            if (isBoundary(c)) {
                return pos + 1;
            }
        }
        return targetEnd;
    }

    private boolean isBoundary(char c) {
        return c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？' || c == '\n';
    }
}
