package com.nageoffer.ai.ragent.core.controller;

import com.nageoffer.ai.ragent.core.dto.kb.KnowledgeBaseCreateReqDTO;
import com.nageoffer.ai.ragent.core.framework.convention.Result;
import com.nageoffer.ai.ragent.core.framework.web.Results;
import com.nageoffer.ai.ragent.core.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/knowledge-base")
    public Result<String> createKnowledgeBase(@RequestBody KnowledgeBaseCreateReqDTO requestParam) {
        return Results.success(knowledgeBaseService.create(requestParam));
    }
}
