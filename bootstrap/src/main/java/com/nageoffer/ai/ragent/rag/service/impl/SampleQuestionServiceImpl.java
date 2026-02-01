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

package com.nageoffer.ai.ragent.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.rag.controller.vo.SampleQuestionVO;
import com.nageoffer.ai.ragent.rag.dao.entity.SampleQuestionDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.SampleQuestionMapper;
import com.nageoffer.ai.ragent.rag.service.SampleQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SampleQuestionServiceImpl implements SampleQuestionService {

    private static final int DEFAULT_LIMIT = 3;

    private final SampleQuestionMapper sampleQuestionMapper;

    @Override
    public List<SampleQuestionVO> listRandomQuestions() {
        List<SampleQuestionDO> records = sampleQuestionMapper.selectList(
                Wrappers.lambdaQuery(SampleQuestionDO.class)
                        .eq(SampleQuestionDO::getDeleted, 0)
                        .last("ORDER BY RAND() LIMIT " + DEFAULT_LIMIT)
        );
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .map(record -> SampleQuestionVO.builder()
                        .id(String.valueOf(record.getId()))
                        .title(record.getTitle())
                        .description(record.getDescription())
                        .question(record.getQuestion())
                        .build())
                .toList();
    }
}
