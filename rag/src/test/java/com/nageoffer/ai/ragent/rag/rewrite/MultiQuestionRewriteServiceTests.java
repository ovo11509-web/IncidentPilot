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

package com.nageoffer.ai.ragent.rag.rewrite;

import com.nageoffer.ai.ragent.rag.core.rewrite.MultiQuestionRewriteService;
import com.nageoffer.ai.ragent.rag.core.rewrite.QueryTermMappingService;
import com.nageoffer.ai.ragent.rag.core.rewrite.RewriteResult;
import com.nageoffer.ai.ragent.rag.config.RAGConfigProperties;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.enums.Tier;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MultiQuestionRewriteServiceTests {

    @Test
    public void shouldReturnRewriteAndSubQuestions() {
        String question = "你好呀，淘宝和天猫数据安全怎么做的？";
        LLMService llm = mock(LLMService.class);
        when(llm.chat(any(ChatRequest.class), eq(Tier.FAST))).thenReturn("""
                {"rewrite":"淘宝和天猫的数据安全措施", "sub_questions":["淘宝数据安全措施", "天猫数据安全措施"]}
                """);
        RewriteResult result = service(llm).rewriteWithSplit(question);

        Assertions.assertEquals("淘宝和天猫的数据安全措施", result.rewrittenQuestion());
        Assertions.assertEquals(List.of("淘宝数据安全措施", "天猫数据安全措施"), result.subQuestions());
    }

    @Test
    void invalidModelOutputFallsBackToNormalizedQuestion() {
        LLMService llm = mock(LLMService.class);
        when(llm.chat(any(ChatRequest.class), eq(Tier.FAST))).thenReturn("not-json");
        RewriteResult result = service(llm).rewriteWithSplit("淘宝和天猫数据安全怎么做？");
        Assertions.assertEquals("淘宝和天猫数据安全怎么做？", result.rewrittenQuestion());
        Assertions.assertEquals(List.of("淘宝和天猫数据安全怎么做？"), result.subQuestions());
    }

    private static MultiQuestionRewriteService service(LLMService llm) {
        var properties = new RAGConfigProperties();
        properties.setQueryRewriteEnabled(true);
        var mapping = mock(QueryTermMappingService.class);
        when(mapping.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        var prompts = mock(PromptTemplateLoader.class);
        when(prompts.load(anyString())).thenReturn("system");
        return new MultiQuestionRewriteService(llm, properties, mapping, prompts);
    }
}
