package com.nageoffer.ai.ragent.controller;

import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.service.RAGEnterpriseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAGStandardController + MCP工具调用 + 记忆系统 + 上下文管理
 */
@RestController
@RequiredArgsConstructor
public class RAGEnterpriseController {

    private final RAGEnterpriseService ragEnterpriseService;

    /**
     * 以 SSE 方式发起对话并持续推送响应
     */
    @GetMapping(value = "/rag/v3/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam String question,
                           @RequestParam(required = false) String conversationId,
                           @RequestParam(required = false) Boolean deepThinking) {
        SseEmitter emitter = new SseEmitter(0L);
        ragEnterpriseService.streamChat(question, conversationId, deepThinking, emitter);
        return emitter;
    }

    /**
     * 停止指定任务的执行
     */
    @PostMapping(value = "/rag/v3/stop")
    public Result<Void> stop(@RequestParam String taskId) {
        ragEnterpriseService.stopTask(taskId);
        return Results.success();
    }
}
