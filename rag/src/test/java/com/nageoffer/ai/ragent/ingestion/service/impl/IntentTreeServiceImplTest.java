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

package com.nageoffer.ai.ragent.ingestion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nageoffer.ai.ragent.audit.support.BizChangeLogContext;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.IntentTreeFactory;
import com.nageoffer.ai.ragent.rag.core.intent.IntentTreeCacheManager;
import com.nageoffer.ai.ragent.rag.dao.entity.IntentNodeDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.IntentNodeMapper;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.rag.enums.IntentLevel;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class IntentTreeServiceImplTest {
    private final IntentNodeMapper mapper = mock(IntentNodeMapper.class);
    private final KnowledgeBaseMapper knowledge = mock(KnowledgeBaseMapper.class);
    private final IntentTreeCacheManager cache = mock(IntentTreeCacheManager.class);
    private final BizChangeLogContext audit = mock(BizChangeLogContext.class);
    private final IntentTreeServiceImpl service = new IntentTreeServiceImpl(mapper, knowledge, cache, audit);

    @BeforeAll
    static void metadata() {
        Configuration config = new Configuration(); config.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(config, ""), IntentNodeDO.class);
    }

    @Test
    void emptyTreeIsEmptyAndReadOnly() {
        when(mapper.selectList(any())).thenReturn(List.of());
        assertEquals(List.of(), service.getFullTree());
        verifyNoInteractions(cache, knowledge, audit);
        verify(mapper, never()).insert(any(IntentNodeDO.class));
    }

    @Test
    void buildsParentChildTreeAndNormalizesCollectionBindings() {
        var root = IntentNodeDO.builder().id("1").intentCode("root").name("root").build();
        var child = IntentNodeDO.builder().id("2").intentCode("child").parentCode("root")
                .collectionNames(List.of(" a ", "a", "b")).build();
        var leaf = IntentNodeDO.builder().id("3").intentCode("leaf").parentCode("child")
                .collectionName(" legacy ").build();
        when(mapper.selectList(any())).thenReturn(List.of(root, child, leaf));
        var tree = service.getFullTree();
        assertEquals(1, tree.size()); assertEquals("root", tree.get(0).getIntentCode());
        var actualChild = tree.get(0).getChildren().get(0);
        assertEquals("child", actualChild.getIntentCode());
        assertEquals(List.of("a", "b"), actualChild.getCollectionNames());
        assertEquals(List.of("legacy"), actualChild.getChildren().get(0).getCollectionNames());
        ArgumentCaptor<LambdaQueryWrapper<IntentNodeDO>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        String sql = query.getValue().getCustomSqlSegment().toLowerCase();
        assertTrue(sql.contains("deleted") && sql.contains("order by sort_order asc,id asc"), sql);
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(0));
        verifyNoInteractions(cache, knowledge, audit);
    }

    @Test
    void initializationSkipsExistingNodesWithoutWrites() {
        try (var factory = mockStatic(IntentTreeFactory.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            factory.when(IntentTreeFactory::buildIntentTree).thenReturn(fixture());
            when(mapper.selectCount(any())).thenReturn(1L);
            assertEquals(0, service.initFromFactory());
            verify(mapper, never()).insert(any(IntentNodeDO.class));
            verifyNoInteractions(cache, knowledge, audit);
        }
    }

    @Test
    void initializationWritesParentFirstAndSecondRunDoesNotWriteAgain() {
        var stored = new HashSet<String>(); var inserted = new ArrayList<IntentNodeDO>();
        when(mapper.selectCount(any())).thenAnswer(call -> {
            LambdaQueryWrapper<IntentNodeDO> query = call.getArgument(0);
            query.getCustomSqlSegment();
            return query.getParamNameValuePairs().values().stream().anyMatch(stored::contains) ? 1L : 0L;
        });
        when(mapper.insert(any(IntentNodeDO.class))).thenAnswer(call -> {
            IntentNodeDO node = call.getArgument(0); node.setId("local-"+node.getIntentCode());
            inserted.add(node); stored.add(node.getIntentCode()); return 1;
        });
        try (var factory = mockStatic(IntentTreeFactory.class, withSettings().mockMaker(org.mockito.MockMakers.INLINE))) {
            factory.when(IntentTreeFactory::buildIntentTree).thenReturn(fixture());
            assertEquals(2, service.initFromFactory());
            assertEquals(0, service.initFromFactory());
        }
        assertEquals(List.of("root", "child"), inserted.stream().map(IntentNodeDO::getIntentCode).toList());
        assertNull(inserted.get(0).getParentCode()); assertEquals("root", inserted.get(1).getParentCode());
        assertEquals(List.of(0, 1), inserted.stream().map(IntentNodeDO::getSortOrder).toList());
        assertEquals(List.of(0, 1), inserted.stream().map(IntentNodeDO::getLevel).toList());
        verify(mapper, times(2)).insert(any(IntentNodeDO.class));
        verify(cache, times(2)).clearIntentTreeCache();
        verify(audit).put("local-root", null, inserted.get(0));
        verify(audit).put("local-child", null, inserted.get(1));
        verifyNoInteractions(knowledge);
    }

    private static List<IntentNode> fixture() {
        var child = IntentNode.builder().id("child").parentId("root").name("child")
                .kind(IntentKind.KB).level(IntentLevel.CATEGORY).build();
        var root = IntentNode.builder().id("root").name("root").kind(IntentKind.KB)
                .level(IntentLevel.DOMAIN).children(List.of(child)).build();
        return List.of(root);
    }
}
