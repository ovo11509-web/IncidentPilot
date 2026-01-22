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

package com.nageoffer.ai.ragent.ingestion.domain.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 文档分块实体类
 * 表示从原始文档中切分出的单个文本块，包含块内容、位置信息、元数据和向量嵌入等信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    /**
     * 块的唯一标识符
     */
    private String chunkId;

    /**
     * 块在文档中的序号索引，从0开始
     */
    private int index;

    /**
     * 块的原始文本内容
     */
    private String content;

    /**
     * 经过增强处理后的文本内容
     * 可能包含上下文增强、摘要等处理后的内容
     */
    private String enhancedContent;

    /**
     * 块在原始文档中的起始字符偏移量
     */
    private int startOffset;

    /**
     * 块在原始文档中的结束字符偏移量
     */
    private int endOffset;

    /**
     * 块的元数据信息
     * 可包含来源信息、页码、标题等附加属性
     */
    private Map<String, Object> metadata;

    /**
     * 块的向量嵌入表示
     * 用于向量相似度检索的浮点数数组
     */
    private float[] embedding;
}
