# 存储日志卷修复与启停留存记录

2026-10-17。用户继续阶段9后的下一步，执行Spec M T033～T037。**已复现原模板自动生成匿名`/logs`卷的机制；显式命名卷修复后，三轮依赖重建启停的完整卷集合稳定，日志标记留存。** 新源码候选单独生成和验证，旧候选、旧源码、旧实例及全部原卷保留；未提交、Tag、推送或部署。

## 身份、范围与取证

- 本轮run：`847db56e-077d-4b8b-b77c-61412885a6c1`；证据根为`bootstrap/target/storage-volume-fix/847db56e-077d-4b8b-b77c-61412885a6c1/`，下文辅助文件名相对此目录。
- 起点：原库1900文件、16个既有容器、81个卷；原候选`01166dd3-0a4c-4def-83dd-688e6c15e8bd`四文件和旧1890文件源码另行校验，旧candidate-proof九个私有配置/标记仅取摘要，不输出凭据。
- 原模板取证使用新project `incidentpilot-volume-probe-847db56e077d4b8bb77c61412885a6c1`，只创建存储容器、从未启动进程，随后Compose down保留卷；不挂载旧匿名卷。
- 成功取证容器`00fffeb67574faba3c533b3b60218825723645f35963a1a8c88a11b2a511edb7`的Mounts显示：`/data`为该project的`storage-data`；`/logs`为随机64位匿名卷。镜像ID为`sha256:f5b68544026308a53b71104c8cf56ae548472d0624ae4c9b32fc812292611ee8`，声明`/data`、`/logs`。详见`probe-proof.json`。
- 这直接证实当前固定镜像与原模板组合的机制；不能追溯已删除旧容器与旧`e98d…`匿名卷的唯一对应关系，旧历史归属继续TBD。此前首次静态采集异常也未因此获得修复结论。

## 核心改动

`resources/local-demo/compose.yaml`保留`storage-data:/data`，增加`storage-logs:/logs`及相同instance标签。新实例为5个命名卷，沿用项目名/instance双重归属检查与Stop保留规则。`scripts/local-demo.ps1`没有变化，不增加迁移、清理、目录bind或新依赖。

`scripts/test-local-demo.ps1`增加4项合同：实际Compose JSON中的双显式挂载、精确5卷及实例标签、外来日志卷拒绝、同实例日志卷接受。检查发生在CLI解析/受控适配层，不启动Docker服务。首次在原模板上按预期失败`STORAGE_DATA_AND_LOGS_EXPLICIT`，修复后30项合同及Node代理2/2通过，退出0；无跳过。

新模板只对新的初始化实例生效。旧实例的私有Compose和初始化摘要保持，不通过修改摘要绕过漂移检查；本轮不自动升级任何旧实例。命名日志卷仍会积累日志，本轮没有容量上限、轮转或删除策略验收。

## 实际运行结果

新Name为`volume-fix-847db56e`，instanceId为`600efe90398c44d3a0e77dc24df6833f`。从当前修复源码调用真实Init/Stop入口，依赖重启使用相同已校验Manifest的Compose up，显式`--pull never`。只启动四个依赖，未构建或启动JVM/前端、未提交告警或触发诊断/动作。

| 检查 | 实际结果 |
| --- | --- |
| 三轮容器重建 | 每轮4个新容器身份，全部运行；每次Stop后自有容器0 |
| 全量挂载 | 每个镜像实际声明路径均被本实例命名卷覆盖；所有Mounts只指向预期5卷，存储`/data`与`/logs`分别独立映射；无匿名挂载 |
| 日志留存 | 第一轮写入本轮专用日志标记，后两轮新容器读取相同标记；配置和凭据摘要保持 |
| 完整卷集合 | 修复实例初始化前为84卷，允许新增5卷后为89；三轮运行中及停止后均严格等于同一89卷集合，不只检查项目前缀数量 |
| 空业务事实 | 每轮READ ONLY SQL核对告警、事件、诊断、动作、执行、回执、时间线均0；模拟应用0、目标PAUSED |
| 停止结果 | 新实例已停止，5个命名卷和取证留下的卷全部保留；旧16容器ID/名称/运行状态/启动时刻及旧81卷保护由收尾记录核对 |

`runtime-proof.json`为`THREE_DEPENDENCY_RECREATE_CYCLES_PASS`，逐轮保存Mounts、image/container ID、运行中/停止后的完整卷名集合、事实与配置摘要。修复后的实例新增匿名卷为0。全局81→89的8个新增卷分别为修复实例5个命名卷，以及原机制取证尝试保留的1个命名卷和2个匿名卷；没有为取得相等结果删除任何卷。

## 实际命令与失败保留

```powershell
pwsh -NoProfile -File scripts/test-local-demo.ps1
pwsh -NoProfile -File scripts/test-release-candidate.ps1
pwsh -NoProfile -File bootstrap/target/storage-volume-fix/847db56e-077d-4b8b-b77c-61412885a6c1/probe.ps1
pwsh -NoProfile -File bootstrap/target/storage-volume-fix/847db56e-077d-4b8b-b77c-61412885a6c1/runtime.ps1 -Resume
```

候选安全合同为`CANDIDATE_CONTRACT_PASS 49`、退出0。首次合同红灯和辅助执行失败均保留，不把它们计入通过样本：

- 基线辅助脚本局部变量碰到库参数`Name`验证，写基线前拒绝；更名后成功，未操作资源。
- 取证辅助脚本先执行自有create/down，后因日志路径不在DemoBase而拒绝；实际检查自有容器已移除、两个新卷保留，改为在本轮目录直接保存辅助日志，再复用同一停止project完成Mounts取证。见`probe-attempt1.json`。
- 首次运行辅助函数`Cli`与PowerShell `cli`别名冲突；已确认`Init`路径不存在、新实例尚未创建。改为唯一函数名，保留`runtime-attempt1.json`。
- 第二次辅助检查将Redis镜像的JSON `null`声明卷误变为一个null路径；实例已停止且5卷保留。修正为空集合后继续严格检查所有真实声明路径和Mounts，未放宽实际挂载断言。见`runtime-attempt2.json`、`auxiliary-corrections.json`。

实际完成的是后续三轮完整依赖循环，不是抹除失败后的首次全绿；`runtime-proof.json`明确记录续跑。辅助脚本属于ignored运行证据，不进入产品候选。

## 新候选与结论边界

预演候选`128a0450-a0ba-4a4f-8839-b761f364d780`已Create/Verify及全量解包核对1894成员，差异0；包内Compose和生命周期脚本与三轮实际测试字节相同。文档收尾后再固定最终四文件候选，身份、实际文件数及四文件摘要放在本轮证据根的`final-candidate.json`，不将自身ID回填被哈希源码；`preflight-candidate.json`保留此前时点。解包副本中的独立Verify也实际执行，不依赖原源码目录。

最终原库/旧候选/旧私有配置/旧1890源码、资源集合、相对链接和任务结果由同目录`final-closeout.json`记录。原候选及历史WITHHELD状态不回填，新候选也不夹带原Git或历史bundle；衍生历史仍按已有单独材料管理。

本轮只证明本机缓存镜像下的挂载机制、修复和依赖生命周期留存，不证明完整新候选应用构建、JVM业务主链、浏览器回归、新Backend/All、第二机器、跨平台或生产运行。旧首次静态异常根因与旧匿名卷历史具体归属仍TBD。
