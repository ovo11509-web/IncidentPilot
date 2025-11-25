package com.nageoffer.ai.ragent.core.service;

import com.nageoffer.ai.ragent.core.dao.entity.KnowledgeDocumentDO;
import com.nageoffer.ai.ragent.core.dto.kb.KnowledgeDocumentCreateReqDTO;
import com.nageoffer.ai.ragent.core.dto.kb.KnowledgeDocumentUpdateReqDTO;

public interface KnowledgeDocumentService {

    Long create(KnowledgeDocumentCreateReqDTO req, String operator);

    void update(Long id, KnowledgeDocumentUpdateReqDTO req, String operator);

    void delete(Long id, String operator);

    KnowledgeDocumentDO getById(Long id);
}
