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

package com.nageoffer.ai.ragent.rag.core.vector;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class MilvusVectorStoreAdminTest {
    private final MilvusClientV2 client = mock(MilvusClientV2.class);
    private final RAGDefaultProperties properties = properties();
    private final MilvusVectorStoreAdmin admin = new MilvusVectorStoreAdmin(client, properties);

    @Test
    void existingSharedCollectionDoesNotCreateAnotherCollection() {
        when(client.hasCollection(any())).thenReturn(true);
        admin.ensureVectorSpace(new VectorSpaceSpec());
        ArgumentCaptor<HasCollectionReq> query = ArgumentCaptor.forClass(HasCollectionReq.class);
        verify(client).hasCollection(query.capture());
        assertEquals("local-shared",query.getValue().getCollectionName());
        verify(client,never()).createCollection(any());
    }

    @Test
    void missingCollectionUsesActualSharedSchemaAndConfiguredDimension() {
        when(client.hasCollection(any())).thenReturn(false);
        admin.ensureVectorSpace(new VectorSpaceSpec());
        ArgumentCaptor<CreateCollectionReq> request = ArgumentCaptor.forClass(CreateCollectionReq.class);
        verify(client).createCollection(request.capture());
        var actual = request.getValue();
        assertEquals("local-shared",actual.getCollectionName());
        assertEquals("id",actual.getPrimaryFieldName());
        assertEquals("embedding",actual.getVectorFieldName());
        var fields = actual.getCollectionSchema().getFieldSchemaList();
        assertEquals(List.of("id","collection_name","content","metadata","embedding"),
                fields.stream().map(f -> f.getName()).toList());
        assertEquals(DataType.VarChar,fields.get(0).getDataType());
        assertEquals(20,fields.get(0).getMaxLength());
        assertEquals(64,fields.get(1).getMaxLength());
        assertEquals(DataType.JSON,fields.get(3).getDataType());
        assertEquals(DataType.FloatVector,fields.get(4).getDataType());
        assertEquals(3,fields.get(4).getDimension());
        var primary = JsonParser.parseString(new Gson().toJson(fields.get(0))).getAsJsonObject();
        assertTrue(primary.get("isPrimaryKey").getAsBoolean());
        assertFalse(primary.get("autoID").getAsBoolean());
        var indexes = actual.getIndexParams();
        assertEquals(2,indexes.size());
        assertEquals("embedding",indexes.get(0).getFieldName());
        assertEquals(IndexParam.IndexType.HNSW,indexes.get(0).getIndexType());
        assertEquals(IndexParam.MetricType.COSINE,indexes.get(0).getMetricType());
        assertEquals("collection_name",indexes.get(1).getFieldName());
        assertEquals(IndexParam.IndexType.INVERTED,indexes.get(1).getIndexType());
    }

    @Test
    void sdkFailurePropagatesWithoutCreatingOrRetrying() {
        var failure = new IllegalStateException("fixed-sdk-failure");
        when(client.hasCollection(any())).thenThrow(failure);
        assertSame(failure,assertThrows(IllegalStateException.class,
                () -> admin.ensureVectorSpace(new VectorSpaceSpec())));
        verify(client).hasCollection(any());
        verify(client,never()).createCollection(any());
    }

    private static RAGDefaultProperties properties() {
        var p = new RAGDefaultProperties();
        p.setCollectionName("local-shared");p.setDimension(3);p.setMetricType("COSINE");
        return p;
    }
}
