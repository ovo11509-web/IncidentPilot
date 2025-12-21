package com.nageoffer.ai.ragent.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationVO {

    private String conversationId;

    private String title;

    private Date lastTime;

    private Date createTime;

    private Date updateTime;
}
