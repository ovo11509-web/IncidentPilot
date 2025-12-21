package com.nageoffer.ai.ragent.controller;

import com.nageoffer.ai.ragent.controller.request.ConversationCreateRequest;
import com.nageoffer.ai.ragent.controller.request.ConversationUpdateRequest;
import com.nageoffer.ai.ragent.controller.vo.ConversationMessageVO;
import com.nageoffer.ai.ragent.controller.vo.ConversationVO;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.service.ConversationHistoryService;
import com.nageoffer.ai.ragent.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationHistoryService conversationHistoryService;
    private final ConversationService conversationService;

    @GetMapping("/conversations")
    public Result<List<ConversationVO>> listConversations() {
        return Results.success(conversationHistoryService.listConversations(UserContext.getUserId()));
    }

    @PostMapping("/conversations")
    public Result<ConversationVO> create(@RequestBody(required = false) ConversationCreateRequest request) {
        return Results.success(conversationService.create(UserContext.getUserId(), request));
    }

    @PutMapping("/conversations/{conversationId}")
    public Result<Void> rename(@PathVariable String conversationId,
                               @RequestBody ConversationUpdateRequest request) {
        conversationService.rename(conversationId, UserContext.getUserId(), request);
        return Results.success();
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> delete(@PathVariable String conversationId) {
        conversationService.delete(conversationId, UserContext.getUserId());
        return Results.success();
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Result<List<ConversationMessageVO>> listMessages(@PathVariable String conversationId,
                                                            @RequestParam(defaultValue = "false") boolean includeSummary) {
        return Results.success(conversationHistoryService.listMessages(conversationId, UserContext.getUserId(), includeSummary));
    }
}
