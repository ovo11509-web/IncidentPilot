package com.nageoffer.ai.ragent.rag.Intent;

import com.nageoffer.ai.ragent.rag.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.intent.NodeScore;
import com.nageoffer.ai.ragent.rag.intent.ParallelIntentClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 并行意图分类器测试
 * <p>
 * 测试按 Domain 拆分并行调用 LLM 的意图识别效果
 */
@Slf4j
@SpringBootTest
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ParallelIntentClassifierTests {

    private final ParallelIntentClassifier parallelIntentClassifier;

    private static final double MIN_SCORE = 0.35;
    private static final int TOP_N = 5;

    /**
     * 场景 1：考勤 + 处罚 混合语义
     */
    @Test
    public void classifyAttendancePunishment() {
        String question = "早上九点十分打卡，有什么处罚？";
        runCase(question);
    }

    /**
     * 场景 2：典型 IT 支持问题
     */
    @Test
    public void classifyItSupportQuestion() {
        String question = "Mac电脑打印机怎么连？";
        runCase(question);
    }

    /**
     * 场景 3：中间件环境信息（Redis）
     */
    @Test
    public void classifyMiddlewareRedisQuestion() {
        String question = "测试环境 Redis 地址是多少？";
        runCase(question);
    }

    /**
     * 场景 4：业务系统（OA），功能 + 安全
     * 期望：高分主要落在 biz-oa-intro / biz-oa-security 上
     */
    @Test
    public void classifyBizSystemQuestion() {
        String question = "OA 系统主要提供哪些功能？数据安全怎么做的？";
        runCase(question);
    }

    /**
     * 场景 5：跨 Domain 多意图问题
     * 期望：并行模式能识别出多个 Domain 的意图
     */
    @Test
    public void classifyCrossDomainQuestion() {
        String question = "OA 系统主要提供哪些功能？测试环境 Redis 地址是多少？";
        runCase(question);
    }

    /**
     * 场景 6：刻意搞一个"非常泛"的问题，看是否整体得分偏低
     */
    @Test
    public void classifyUncorrelatedQuestion() {
        String question = "公司团建一般怎么安排？";
        runCase(question);
    }

    /**
     * 场景 7：刻意搞一个不相关的问题
     * 期望：各 Domain 都返回空数组，最终无结果
     */
    @Test
    public void classifyGeneralQuestion() {
        String question = "阿巴阿巴";
        runCase(question);
    }

    /**
     * 场景 8：咨询 Chat 场景
     */
    @Test
    public void classifyHelloQuestion() {
        String question = "你是谁？";
        runCase(question);
    }

    /**
     * 场景 9：单一 Domain 问题
     * 期望：只有一个 Domain 返回结果
     */
    @Test
    public void classifySingleDomainQuestion() {
        String question = "公司发票抬头是什么？";
        runCase(question);
    }

    // ======================== 工具方法 ========================

    private void runCase(String question) {
        long start = System.nanoTime();
        List<NodeScore> topKScores = parallelIntentClassifier.topKAboveThreshold(
                question, TOP_N, MIN_SCORE
        );
        long end = System.nanoTime();

        double totalMs = (end - start) / 1_000_000.0;

        double maxScore = topKScores.stream()
                .mapToDouble(NodeScore::getScore)
                .max()
                .orElse(0.0);

        boolean needRag = maxScore >= MIN_SCORE;

        System.out.println("==================================================");
        System.out.println("[ParallelIntentClassifier] Question: " + question);
        System.out.println("--------------------------------------------------");
        System.out.println("MIN_SCORE : " + MIN_SCORE);
        System.out.println("TOP_N     : " + TOP_N);
        System.out.println("MaxScore  : " + maxScore);
        System.out.println("Need RAG  : " + needRag);
        System.out.println();

        System.out.println(">> Candidates after filter (score >= " + MIN_SCORE + "):");
        if (topKScores.isEmpty()) {
            System.out.println("  (none, 可以考虑不走 RAG 或走 fallback)");
        } else {
            topKScores.forEach(ns -> {
                IntentNode n = ns.getNode();
                System.out.printf("  - %.4f  |  %s  (id=%s)%n",
                        ns.getScore(),
                        safeFullPath(n),
                        n.getId());
            });
        }

        System.out.println();
        System.out.println("---- Perf (Parallel Mode) ----");
        System.out.printf("Total cost: %.2f ms%n", totalMs);
        System.out.println("==================================================\n");
    }

    private String safeFullPath(IntentNode node) {
        if (node == null) return "null";
        return node.getFullPath() != null ? node.getFullPath() : node.getName();
    }
}
