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
 * 文本块富集类型枚举
 * 定义对文档分块进行富集处理的类型，用于增强分块的元数据和检索能力
 */
public enum ChunkEnrichType {

    /**
     * 关键词提取 - 从文本块中提取关键词
     */
    KEYWORDS,

    /**
     * 摘要生成 - 为文本块生成摘要
     */
    SUMMARY,

    /**
     * 元数据添加 - 为文本块添加额外的元数据信息
     */
    METADATA
}
