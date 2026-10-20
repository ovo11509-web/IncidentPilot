# 本地候选验证指南

## 前置条件

PowerShell7、Git、Java21、Maven Wrapper及已缓存依赖。以下从仓库根目录执行；不启动/停止现有演示、不连远程服务。candidateId以实际Create输出为准。

## 契约与当前版本

```powershell
pwsh -NoProfile -File scripts/test-local-demo.ps1
pwsh -NoProfile -File scripts/test-release-candidate.ps1
./mvnw.cmd -o -Dspotless.apply.skip=true validate
./mvnw.cmd -o -Dspotless.apply.skip=true -DskipTests package
```

确认9POM及前端版本0.1.0，bootstrap/mcp-server准确版本JAR存在。记录实际命令/退出码；跳过测试不表示新业务回归。

## 创建与校验

```powershell
pwsh -NoProfile -File scripts/new-release-candidate.ps1 -Command Create
pwsh -NoProfile -File scripts/new-release-candidate.ps1 -Command Verify -CandidatePath bootstrap/target/release-candidates/<candidateId>
```

先稳定源码和文档再创建，不覆盖既有候选。核对仅有manifest、ZIP、candidate、SHA256SUMS四文件。Create状态为 `SOURCE_CANDIDATE_READY`，Verify成功为 `SOURCE_VERIFIED_HISTORY_WITHHELD`；history必须标 `WITHHELD_POTENTIAL_HISTORICAL_SECRETS`，保存baselineHead/branch/upstream及原仓库历史保留事实。历史静态审查发现潜在凭据，禁止附加或自行打包分发history.bundle。不得发送rawlogs、credentials或演示卷。

## 解包离线构建

Verify成功后在忽略目录内新空目录解包；所有成员必须位于目标内。进入解包根执行相同 `-o -Dspotless.apply.skip=true -DskipTests package`，构建结果存候选外。记录候选摘要、解包完整性、工具版本、缓存复用、退出码及两JAR准确版本。依赖缺失则记录受阻，不能暗中联网。

配置模板及local-demo帮助说明初始化时生成新私有配置；作者credentials不交付。解包构建是仅源码范围，不要求或生成历史包，不能按完整Git克隆使用。完整历史交付需后续单独安全方案，原仓库历史不得改写/删除；第1步全部交付尚未闭合。独立环境初始化、登录、两告警聚合、诊断、拒绝零应用、批准一次应用和重启数据保留属于后续第2步；跨平台/CI第3步。历史Spec L注明原runId/sourceDigest，不作为新candidate Backend/All。提交/Tag/推送需另行授权。
