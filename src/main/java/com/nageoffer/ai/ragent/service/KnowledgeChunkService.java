package com.nageoffer.ai.ragent.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.controller.request.KnowledgeChunkBatchRequest;
import com.nageoffer.ai.ragent.controller.request.KnowledgeChunkCreateRequest;
import com.nageoffer.ai.ragent.controller.request.KnowledgeChunkPageRequest;
import com.nageoffer.ai.ragent.controller.request.KnowledgeChunkUpdateRequest;
import com.nageoffer.ai.ragent.controller.vo.KnowledgeChunkVO;

import java.util.List;

/**
 * 知识库 Chunk 服务接口
 */
public interface KnowledgeChunkService {

    /**
     * 分页查询 Chunk 列表
     */
    IPage<KnowledgeChunkVO> pageQuery(String docId, KnowledgeChunkPageRequest requestParam);

    /**
     * 新增 Chunk
     */
    KnowledgeChunkVO create(String docId, KnowledgeChunkCreateRequest requestParam);

    /**
     * 批量新增
     */
    void batchCreate(String docId, List<KnowledgeChunkCreateRequest> requestParams);

    /**
     * 更新 Chunk 内容
     */
    void update(String docId, String chunkId, KnowledgeChunkUpdateRequest requestParam);

    /**
     * 删除 Chunk
     */
    void delete(String docId, String chunkId);

    /**
     * 启用/禁用单条 Chunk
     */
    void enableChunk(String docId, String chunkId, boolean enabled);

    /**
     * 批量启用 Chunk
     */
    void batchEnable(String docId, KnowledgeChunkBatchRequest requestParam);

    /**
     * 批量禁用 Chunk
     */
    void batchDisable(String docId, KnowledgeChunkBatchRequest requestParam);

    /**
     * 重建文档向量（以 MySQL enabled=1 的 chunk 为准）
     */
    void rebuildByDocId(String docId);
}
