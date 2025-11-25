package com.nageoffer.ai.ragent.core.service;

import com.nageoffer.ai.ragent.core.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.core.dto.kb.KnowledgeBaseCreateReqDTO;
import com.nageoffer.ai.ragent.core.dto.kb.KnowledgeBaseUpdateReqDTO;

public interface KnowledgeBaseService {

    String create(KnowledgeBaseCreateReqDTO requestParam);

    void update(KnowledgeBaseUpdateReqDTO req);

    void delete(String id);

    KnowledgeBaseDO getById(String id);
}
