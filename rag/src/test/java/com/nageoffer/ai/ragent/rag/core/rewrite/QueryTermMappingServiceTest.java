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

package com.nageoffer.ai.ragent.rag.core.rewrite;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.rag.dao.entity.QueryTermMappingDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.QueryTermMappingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class QueryTermMappingServiceTest {
    private final QueryTermMappingMapper mapper = mock(QueryTermMappingMapper.class);
    private final QueryTermMappingCacheManager cache = mock(QueryTermMappingCacheManager.class);
    private final QueryTermMappingService service = new QueryTermMappingService(mapper, cache);

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), QueryTermMappingDO.class);
    }

    @Test
    void blankInputDoesNotReadAnyResource() {
        assertNull(service.normalize(null));
        assertEquals("", service.normalize(""));
        verifyNoInteractions(mapper, cache);
    }

    @Test
    void cachedRuleNormalizesWithoutDuplicatingTarget() {
        when(cache.getMappingsFromCache()).thenReturn(List.of(rule("平安", "平安保司", 1, 1)));
        assertEquals("平安保司与平安保司", service.normalize("平安与平安保司"));
        verifyNoInteractions(mapper);
        verify(cache, never()).saveMappingsToCache(any());
    }

    @Test
    void emptyCacheIsAHitAndDoesNotReadDatabase() {
        when(cache.getMappingsFromCache()).thenReturn(List.of());
        assertEquals("unaltered", service.normalize("unaltered"));
        verifyNoInteractions(mapper);
    }

    @Test
    void missLoadsRulesAndWritesBackExactlyThoseRules() {
        var rules = new ArrayList<>(List.of(rule("旧称", "正式名称", 1, 1)));
        when(cache.getMappingsFromCache()).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(rules);
        assertEquals("正式名称", service.normalize("旧称"));
        verify(mapper).selectList(any());
        verify(cache).saveMappingsToCache(rules);
    }

    @Test
    void disabledUnsupportedAndIncompleteRulesAreIgnored() {
        when(cache.getMappingsFromCache()).thenReturn(List.of(
                rule("x", "disabled", 0, 1), rule("x", "unsupported", 1, 3),
                rule("", "empty-source", 1, 1), rule("x", "", 1, 1),
                rule("x", "unset-enabled", null, 1)));
        assertEquals("x", service.normalize("x"));
        verifyNoInteractions(mapper);
    }

    private static QueryTermMappingDO rule(String from, String to, Integer enabled, int type) {
        var rule = new QueryTermMappingDO();
        rule.setSourceTerm(from); rule.setTargetTerm(to); rule.setEnabled(enabled);
        rule.setMatchType(type); rule.setPriority(1);
        return rule;
    }
}
