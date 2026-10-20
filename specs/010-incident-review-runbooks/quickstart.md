# 验证指南

前置：现有 Java/Maven、frontend/node_modules；本地浏览器使用隔离 fixture，禁止付费模型与生产资源。

1. `node tests/incident-view.check.mjs`（frontend）验证分页、导出与版本匹配。
2. `node node_modules/typescript/bin/tsc --noEmit -p tsconfig.app.json`、`npm run lint`、`npm run build`（frontend）。
3. `./mvnw.cmd -Dspotless.apply.skip=true -pl rag -am -Dtest=RunbookCatalogControllerTest,RunbookCorpusTest,RunbookRetrievalTest -Dsurefire.failIfNoSpecifiedTests=false test`，核对实际目标XML。
4. 管理员：事件详情→查看复盘→完整内容→下载 Markdown；UNKNOWN、失效诊断不转成成功/确认根因。
5. 侧栏→处置手册→搜索→正文；诊断引用→精确版本；错误版本/摘要显示不匹配。
6. 普通用户拒绝读取目录；失败/变化/上限报告不允许导出。

实际执行结果在 implementation-report.md 记录，未执行场景不计通过。
