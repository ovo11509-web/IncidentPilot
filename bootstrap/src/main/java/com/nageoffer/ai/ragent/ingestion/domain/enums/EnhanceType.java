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
 * 文档增强类型枚举
 * 定义对整个文档内容进行增强处理的类型，用于提升文档的检索和理解质量
 */
public enum EnhanceType {

    /**
     * 上下文增强 - 为文本添加上下文信息，提升理解能力
     */
    CONTEXT_ENHANCE,

    /**
     * 关键词提取 - 从文档中提取重要关键词
     */
    KEYWORDS,

    /**
     * 问题生成 - 基于文档内容生成相关问题
     */
    QUESTIONS,

    /**
     * 元数据提取 - 提取文档的元数据信息
     */
    METADATA
}
