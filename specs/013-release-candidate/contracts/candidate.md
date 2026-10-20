# CLI契约

```powershell
pwsh -NoProfile -File scripts/new-release-candidate.ps1 -Command Create
pwsh -NoProfile -File scripts/new-release-candidate.ps1 -Command Verify -CandidatePath bootstrap/target/release-candidates/<candidateId>
pwsh -NoProfile -File scripts/test-release-candidate.ps1
```

Command仅Create/Verify，默认Create。`-Library`只加载函数，不生成制品、不启动服务。

## Create

- 输入当前Git tracked+untracked非忽略文件与明确白名单/拒绝规则，覆盖有效未提交源码、锁文件、Schema、模板、文档、规格和许可。
- 输出不可覆盖新目录，仅source.zip、source-manifest.json、candidate.json、SHA256SUMS四文件。全部写入且核验后退出0，状态为 `SOURCE_CANDIDATE_READY`；历史为 `WITHHELD_POTENTIAL_HISTORICAL_SECRETS`，不包含history.bundle。
- 排除target/dist/node_modules/.git、私有配置、credentials、原始日志、volumes与缓存；拒绝链接、敏感选中内容和复制中源变化，错误不打印具体敏感值。
- Git只读；元数据不得保存带密码的remoteURL，保存baselineHead/branch/upstream及 `historyPreservedInOriginalRepository=true`。原仓库历史不改写或删除；候选暂扣潜在含密历史，完整历史交付需后续单独安全方案。

## Verify

只读既有候选，不依赖当前工作树。核对identity/schema/版本、制品摘要、ZIP内规范路径、每成员长度/内容、无额外/缺失/重名/大小写冲突、禁入路径、历史暂扣状态和基线来源。候选根目录或ZIP出现history.bundle等历史附件均失败。成功结果为 `SOURCE_VERIFIED_HISTORY_WITHHELD`；任何不一致非0，不启动服务、不联网。

## 最小契约集

| 样例 | 结果 |
| --- | --- |
| 合法tracked修改+untracked源码 | 两类均入清单 |
| 生成/私有/原始运行目录 | 不入源码ZIP |
| 白名单内链接/重解析点、真实敏感选中内容 | 失败关闭，不跟随、不打印值 |
| ../、绝对路径、重复/大小写碰撞 | Verify失败 |
| ZIP成员篡改/丢失/额外，清单/制品哈希篡改 | Verify失败 |
| 已有输出目录、复制中源变化 | Create失败，无覆盖/误报成功 |
| 候选或ZIP夹带bundle，元数据掩盖历史暂扣/缺少来源保留事实 | Verify失败 |
| 根版本非法、模块父版本不同、当前JAR缺失仅有旧JAR | local-demo定位失败，无回退 |

## 构建结果

当前树与候选解包后离线package分别记录，禁止格式自动改源码；跳过业务测试明确说明，不称新Backend/All。复用本机缓存不是干净接手者环境。日志留忽略目录，报告仅写安全事实。

## 阶段15：门禁的候选来源

`check-delivery.ps1 -CandidatePath <四文件目录> -Gate <范围> [-CheckOnly]`显式选择候选来源；先执行上述Verify，再比较门禁所在实际源码根。完整路径集合、精确大小写、字节长度、SHA256必须与manifest一致；只剪枝八个模块target、frontend/node_modules、frontend/dist、页面检查生成的frontend/.output/incident和根.git。新增未知文件、隐藏配置、`.output`内其他文件、源码内同名目录、链接或篡改均失败；不能使用打包排除规则静默忽略额外输入。

候选四文件摘要与完整源码在开始及结束分别核验，CheckOnly也必须结束复核；整体替换为另一自洽候选仍失败。此模式不调用或要求Git，run及safe均schema2/head=null，sourceCandidate仅含kind、candidateId、manifestSha256、metadataSha256、zipSha256、checksumsSha256、baselineHead、fileCount；工具/阶段和sourceDigest仍按原规则记录。无显式候选时要求真实Git根，schema1、固定命令和原CI上传白名单保持。

前提CHECKED、Frontend PASS、Backend PASS、完整All PASS分别验收；本轮只承诺真实无Git的All CheckOnly及Frontend四阶段，不继承旧候选的All结果。已有候选、旧接手包及失败证据不改，第二机器和正式Git发布仍待后续执行。

## 本地存储挂载修复合同

新实例的固定RustFS镜像声明卷路径`/data`、`/logs`都须由Compose显式命名挂载覆盖；五个命名卷均有相同instance标签。Stop继续保留数据与日志，不带`--volumes`。原四卷实例及其配置摘要不自动迁移。聚焦合同检查实际Compose解析结果与镜像声明覆盖；运行证据另核对至少三轮依赖重建后的Mounts、日志标记与完整卷集合，不能用静态合同替代实际Docker留存。新源码候选ID与旧候选不同，不覆盖旧四文件或解包副本。
