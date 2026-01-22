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

package com.nageoffer.ai.ragent.ingestion.domain.enums;

/**
 * 文档分块策略枚举
 * 定义将文档内容切分成块的不同策略，适用于不同的文档类型和场景
 */
public enum ChunkStrategy {

    /**
     * 固定大小切分 - 按固定字符数或token数切分
     */
    FIXED_SIZE,

    /**
     * 按句子切分 - 以句子为单位进行切分
     */
    SENTENCE,

    /**
     * 按段落切分 - 以段落为单位进行切分
     */
    PARAGRAPH,

    /**
     * 语义切分 - 基于语义相似度进行智能切分
     */
    SEMANTIC
}
