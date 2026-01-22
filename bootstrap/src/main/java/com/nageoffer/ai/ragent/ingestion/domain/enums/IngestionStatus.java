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
 * 摄取任务状态枚举
 * 定义文档摄取任务的执行状态
 */
public enum IngestionStatus {

    /**
     * 等待中 - 任务已创建但尚未开始执行
     */
    PENDING,

    /**
     * 运行中 - 任务正在执行中
     */
    RUNNING,

    /**
     * 失败 - 任务执行失败
     */
    FAILED,

    /**
     * 已完成 - 任务执行成功完成
     */
    COMPLETED
}
