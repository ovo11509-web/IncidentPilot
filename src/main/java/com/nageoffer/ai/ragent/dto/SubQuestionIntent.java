package com.nageoffer.ai.ragent.dto;

import java.util.List;

import com.nageoffer.ai.ragent.rag.intent.NodeScore;

/**
 * 子问题与其意图候选
 *
 * @param subQuestion 子问题文本
 * @param nodeScores  子问题的意图候选
 */
public record SubQuestionIntent(String subQuestion, List<NodeScore> nodeScores) {
}
