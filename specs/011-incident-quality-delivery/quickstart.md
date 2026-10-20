# 本轮验证

从仓库根目录运行。下列命令关闭 Maven 隐式格式改写，使用独立 Testcontainers，不改连开发库。若 Wrapper 默认缓存不适用，由本机调用者追加 `-Dmaven.repo.local=<已配置的缓存路径>`；不修改全局设置。

```powershell
./mvnw.cmd -o -Dspotless.apply.skip=true -pl incident -am '-Dtest=IncidentFilteringIntegrationTest,DiagnosisQueryPageIntegrationTest,ActionApprovalRaceTest,ActionReceiptTest,ActionUnknownConvergenceTest,AlertToSimulationE2ETest,DiagnosisApiContractTest,DiagnosisPersistenceIntegrationTest,IncidentSnapshotIntegrationTest,IncidentWorkbenchQueryIntegrationTest,IncidentWorkbenchContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw.cmd -o -Dspotless.apply.skip=true -pl bootstrap -am -Dtest=LocalDemoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
pwsh -NoProfile -File scripts/test-local-demo.ps1
node --test resources/local-demo/local-web.test.mjs
```

1. 查询测试使用Maven Wrapper offline、`-Dspotless.apply.skip=true`、incident reactor及目标类。真实两连接用本机Testcontainers，不改连开发库。
2. 在 `frontend/` 运行 `node tests/incident-view.check.mjs`、`node node_modules/typescript/bin/tsc --noEmit -p tsconfig.app.json`、`npm run lint`、`npm run build`。
3. `pwsh -NoProfile -File scripts/test-local-demo.ps1`。
4. `pwsh -NoProfile -File scripts/local-demo.ps1 -Command Init`，再Build、Start、Status、Verify。
5. 本地浏览器核对角色/筛选/报告目标和超时/进度取消/手册；凭据仅从ignored配置读取，不写证据。
6. 记录事件事实，Stop、Start、Verify对照账户、Incident和执行历史保留；不删除卷。
7. 所有阶段与实际平台单独记录。Linux/Node22/Java17/托管CI未运行则不称通过。

详细前置条件、私有凭据读取和生命周期使用方式见 [本机演示说明](../../docs/development/local-demo.md)。首次演练需要显式提交本地告警和服务端审批；`Verify` 本身只有认证及读取，不自动播种或批准动作。实际结果与中断修复见 [实施记录](implementation-report.md)。
