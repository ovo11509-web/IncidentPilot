package com.nageoffer.ai.ragent.rag.intent;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.dao.entity.IntentNodeDO;
import com.nageoffer.ai.ragent.dao.mapper.IntentNodeMapper;
import com.nageoffer.ai.ragent.rag.chat.LLMService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import static com.nageoffer.ai.ragent.constant.RAGConstant.INTENT_CLASSIFIER_DOMAIN_PROMPT;

/**
 * 并行意图分类器
 * <p>
 * 按 Domain（根节点）拆分意图，每个 Domain 独立构建 Prompt 并行调用 LLM，
 * 最后合并所有结果并排序返回。适用于意图数量较多的场景
 * <p>
 * 暂时废弃，并行调用存在一个问题，多个 Domain 之间可能会存在逻辑依赖，拆分后无法全局判断
 */
@Slf4j
@Service("parallelIntentClassifier")
@Deprecated
public class ParallelIntentClassifier implements IntentClassifier {

    private final LLMService llmService;
    private final IntentNodeMapper intentNodeMapper;
    private final ThreadPoolExecutor intentClassifyExecutor;

    /**
     * 根节点列表（Domain 层）
     */
    private List<IntentNode> rootNodes;

    /**
     * 根节点 ID -> 根节点映射
     */
    private Map<String, IntentNode> id2Root;

    /**
     * 按 Domain 分组的叶子节点
     * key: rootId, value: 该 Domain 下的所有叶子节点
     */
    private Map<String, List<IntentNode>> domainLeafNodes;

    /**
     * id -> node 全局映射
     */
    private Map<String, IntentNode> id2Node;

    public ParallelIntentClassifier(
            LLMService llmService,
            IntentNodeMapper intentNodeMapper,
            @Qualifier("intentClassifyThreadPoolExecutor") ThreadPoolExecutor intentClassifyExecutor) {
        this.llmService = llmService;
        this.intentNodeMapper = intentNodeMapper;
        this.intentClassifyExecutor = intentClassifyExecutor;
    }

    @PostConstruct
    public void init() {
        // 1. 从数据库加载意图树
        this.rootNodes = loadIntentTreeFromDB();

        // 2. 构建根节点映射
        this.id2Root = rootNodes.stream()
                .collect(Collectors.toMap(IntentNode::getId, n -> n));

        // 3. 按 Domain 分组叶子节点
        this.domainLeafNodes = new HashMap<>();
        for (IntentNode root : rootNodes) {
            List<IntentNode> leaves = collectLeaves(root);
            if (CollUtil.isNotEmpty(leaves)) {
                domainLeafNodes.put(root.getId(), leaves);
            }
        }

        // 4. 构建全局 id -> node 映射
        List<IntentNode> allNodes = flatten(rootNodes);
        this.id2Node = allNodes.stream()
                .collect(Collectors.toMap(IntentNode::getId, n -> n));

        int totalLeaves = domainLeafNodes.values().stream().mapToInt(List::size).sum();
        log.info("并行意图分类器初始化完成, Domain数: {}, 总叶子节点数: {}",
                domainLeafNodes.size(), totalLeaves);
    }

    @Override
    public List<NodeScore> classifyTargets(String question) {
        if (CollUtil.isEmpty(domainLeafNodes)) {
            return List.of();
        }

        long startTime = System.currentTimeMillis();

        // 为每个 Domain 创建并行任务
        List<CompletableFuture<List<NodeScore>>> futures = domainLeafNodes.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(
                        () -> classifyDomain(question, entry.getKey(), entry.getValue()),
                        intentClassifyExecutor
                ))
                .toList();

        // 等待所有任务完成并收集结果
        List<NodeScore> allScores = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .collect(Collectors.toList());

        long costMs = System.currentTimeMillis() - startTime;
        log.info("并行意图识别完成, 耗时: {}ms, Domain数: {}, 结果数: {}",
                costMs, domainLeafNodes.size(), allScores.size());

        log.info("意图识别结果:\n{}",
                JSONUtil.toJsonPrettyStr(
                        allScores.stream().map(each -> {
                            IntentNode node = each.getNode();
                            node.setChildren(null);
                            return each;
                        }).collect(Collectors.toList())
                )
        );

        return allScores;
    }

    /**
     * 对单个 Domain 进行意图识别
     */
    private List<NodeScore> classifyDomain(String question, String domainId, List<IntentNode> leafNodes) {
        IntentNode root = id2Root.get(domainId);
        String prompt = buildDomainPrompt(question, root, leafNodes);
        String raw;

        try {
            raw = llmService.chat(prompt);
        } catch (Exception e) {
            log.warn("Domain[{}] LLM 调用失败: {}", domainId, e.getMessage());
            return List.of();
        }

        return parseLLMResponse(raw, domainId);
    }

    /**
     * 为指定 Domain 的叶子节点构建 Prompt
     * 使用并行模式专用提示词，传入 Domain 名称和描述
     */
    private String buildDomainPrompt(String question, IntentNode root, List<IntentNode> leafNodes) {
        String domainName = root != null ? root.getName() : "未知领域";
        String domainDesc = root != null && root.getDescription() != null ? root.getDescription() : "无描述";

        StringBuilder sb = new StringBuilder();

        for (IntentNode node : leafNodes) {
            sb.append("- id=").append(node.getId()).append("\n");
            sb.append("  path=").append(node.getFullPath()).append("\n");
            sb.append("  description=").append(node.getDescription()).append("\n");

            if (node.isMCP()) {
                sb.append("  type=MCP\n");
                if (node.getMcpToolId() != null) {
                    sb.append("  toolId=").append(node.getMcpToolId()).append("\n");
                }
            } else if (node.isSystem()) {
                sb.append("  type=SYSTEM\n");
            } else {
                sb.append("  type=KB\n");
            }

            if (node.getExamples() != null && !node.getExamples().isEmpty()) {
                sb.append("  examples=");
                sb.append(String.join(" / ", node.getExamples()));
                sb.append("\n");
            }
            sb.append("\n");
        }

        return INTENT_CLASSIFIER_DOMAIN_PROMPT.formatted(domainName, domainDesc, sb.toString(), question);
    }

    /**
     * 解析 LLM 返回的 JSON 响应
     */
    private List<NodeScore> parseLLMResponse(String raw, String domainId) {
        try {
            JsonElement root = JsonParser.parseString(raw.trim());

            JsonArray arr;
            if (root.isJsonArray()) {
                arr = root.getAsJsonArray();
            } else if (root.isJsonObject() && root.getAsJsonObject().has("results")) {
                arr = root.getAsJsonObject().getAsJsonArray("results");
            } else {
                log.warn("Domain[{}] LLM 返回非预期 JSON 格式: {}", domainId, raw);
                return List.of();
            }

            List<NodeScore> scores = new ArrayList<>();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                if (!obj.has("id") || !obj.has("score")) continue;

                String id = obj.get("id").getAsString();
                double score = obj.get("score").getAsDouble();

                IntentNode node = id2Node.get(id);
                if (node == null) {
                    log.warn("Domain[{}] LLM 返回未知节点 ID: {}", domainId, id);
                    continue;
                }

                scores.add(new NodeScore(node, score));
            }

            return scores;
        } catch (Exception e) {
            log.warn("Domain[{}] 解析 LLM 响应失败: {}", domainId, raw, e);
            return List.of();
        }
    }

    /**
     * 收集指定节点下的所有叶子节点
     */
    private List<IntentNode> collectLeaves(IntentNode node) {
        List<IntentNode> leaves = new ArrayList<>();
        Deque<IntentNode> stack = new ArrayDeque<>();
        stack.push(node);

        while (!stack.isEmpty()) {
            IntentNode n = stack.pop();
            if (n.isLeaf()) {
                leaves.add(n);
            } else if (n.getChildren() != null) {
                for (IntentNode child : n.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return leaves;
    }

    /**
     * 扁平化整棵树
     */
    private List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> result = new ArrayList<>();
        Deque<IntentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            IntentNode n = stack.pop();
            result.add(n);
            if (n.getChildren() != null) {
                for (IntentNode child : n.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return result;
    }

    /**
     * 从数据库加载意图树
     */
    private List<IntentNode> loadIntentTreeFromDB() {
        List<IntentNodeDO> intentNodeDOList = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
        );

        if (intentNodeDOList.isEmpty()) {
            return List.of();
        }

        Map<String, IntentNode> id2Node = new HashMap<>();
        for (IntentNodeDO each : intentNodeDOList) {
            IntentNode node = BeanUtil.toBean(each, IntentNode.class);
            node.setId(each.getIntentCode());
            node.setParentId(each.getParentCode());
            node.setMcpToolId(each.getMcpToolId());
            node.setParamPromptTemplate(each.getParamPromptTemplate());
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            id2Node.put(node.getId(), node);
        }

        List<IntentNode> roots = new ArrayList<>();
        for (IntentNode node : id2Node.values()) {
            String parentId = node.getParentId();
            if (parentId == null || parentId.isBlank()) {
                roots.add(node);
                continue;
            }

            IntentNode parent = id2Node.get(parentId);
            if (parent == null) {
                roots.add(node);
                continue;
            }

            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(node);
        }

        fillFullPath(roots, null);
        return roots;
    }

    /**
     * 填充 fullPath
     */
    private void fillFullPath(List<IntentNode> nodes, IntentNode parent) {
        if (nodes == null) return;

        for (IntentNode node : nodes) {
            if (parent == null) {
                node.setFullPath(node.getName());
            } else {
                node.setFullPath(parent.getFullPath() + " > " + node.getName());
            }

            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                fillFullPath(node.getChildren(), node);
            }
        }
    }
}
