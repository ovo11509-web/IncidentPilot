# 研究结论

2026-10-17，依据当前POM/脚本/Git及Spec L记录；版本研究由并行version audit提供。

## 统一版本与精确JAR

- **决定**：根project.version及8个parent.version为0.1.0，内部依赖保留 `${project.version}`。前端package及lock根版本已一致。local-demo按根版本、模块artifactId/parent一致性精确选文件，仅允许bootstrap/mcp-server。
- **依据**：已核实唯一运行旧JAR名位于local-demo；历史记录中的旧名是来源事实。
- **未采用**：glob/mtime挑包、缺失回退旧包、依赖升级，均不能保证当前版本。

## 完整源码与历史

- **决定**：Git tracked+untracked非忽略清单，再套显式源码/文档/模板白名单与禁入规则；复制前后复核源摘要。只输出四文件源码候选，不输出history.bundle，元数据保存HEAD、上游、分支和原仓库历史保留事实。
- **依据**：git archive HEAD会遗漏未提交新增能力，递归复制会带入私有数据。宪章要求保留上游历史和Apache-2.0。
- **未采用**：擅自commit/stash/reset/clean、复制.git、重新初始化抹除历史、直接分发潜在含密的bundle。原仓库历史原样保留，完整历史交付需要后续单独安全方案。

### 实际审查后的安全调整

静态窄规则扫描检查HEAD可达17870对象/5449 blobs，旧 `src/main/resources/application.yaml` 的16个blob出现两种非占位、高多样性的sk形态api-key字面量。未联网测试，无法证明它们有效或已撤销；不得把结果写成“已确认泄露有效密钥”，也不能因未知失效而默认分发。扫描事实仅保留对象/路径/规则，不记录字面量。README另有占位token不计真实候选；该窄扫描不等于全面秘密认证。

因此原“可选HEAD历史bundle”方案被安全暂扣方案替代：Create为 `SOURCE_CANDIDATE_READY`，history为 `WITHHELD_POTENTIAL_HISTORICAL_SECRETS`；Verify为 `SOURCE_VERIFIED_HISTORY_WITHHELD`。源码快照可继续固定和离线构建，完整历史接手与第1步全部交付闭合仍未完成。不改写/删除原历史，不自动撤销凭据，不教用户重新打包分发疑似含密历史。

## 安全与完整性

- **决定**：规范相对路径、拒绝越界/重复/大小写碰撞/链接/重解析点与敏感选中内容；每文件SHA256+长度，ZIP额外/缺失成员均拒绝。Create不可覆盖，Verify不依赖原源码树。
- **依据**：仅有ZIP总哈希不能发现不安全成员或证明源码清单完整；当前目录可能继续修改。
- **未采用**：只检查文件名、跟随链接、只比较包总哈希。

## 验收归属与构建边界

- **决定**：历史验收仅显式允许字段投影并标原runId/sourceDigest/范围，不复制raw/credentials/volumes；无安全投影时只引用仓库报告。当前树package和候选解包 `-o` package实际执行，关闭测试与格式自动修改。
- **依据**：Spec L对应此前源码；新版本文件管线不自动继承新Backend/All PASS。本机离线缓存复用不能冒充新接手者环境。
- **未采用**：复制整个target证据目录、泛化JSON“脱敏”、暗中联网补依赖仍称离线通过、将同机解包构建称完整端到端复现。
