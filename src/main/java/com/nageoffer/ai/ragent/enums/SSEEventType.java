package com.nageoffer.ai.ragent.enums;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum SSEEventType {

    /**
     * 会话与任务的元信息事件
     */
    META("meta"),

    /**
     * 增量消息事件
     */
    MESSAGE("message"),

    /**
     * 完成事件
     */
    DONE("done"),

    /**
     * 标题事件
     */
    TITLE("title"),

    /**
     * 取消事件
     */
    CANCEL("cancel");

    private final String value;

    /**
     * SSE 事件名称（与前端约定一致）
     */
    public String value() {
        return value;
    }
}
