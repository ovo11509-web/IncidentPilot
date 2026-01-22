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

package com.nageoffer.ai.ragent.ingestion.strategy.parser;

import java.util.Map;

/**
 * 文档解析器接口，定义了不同类型文档的解析行为
 */
public interface DocumentParser {

    /**
     * 获取解析器的类型
     *
     * @return 解析器类型的字符串表示
     */
    String getParserType();

    /**
     * 解析文档内容
     *
     * @param content  文档的二进制字节数组
     * @param mimeType 文档的 MIME 类型
     * @param options  解析选项，用于自定义解析过程
     * @return 解析结果，包含提取的文本及其元数据
     */
    ParseResult parse(byte[] content, String mimeType, Map<String, Object> options);
}
