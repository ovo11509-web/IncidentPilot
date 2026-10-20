# 测试治理数据模型

不新增数据库表、生产实体或API。以下为文档/测试证据概念。

## 探针覆盖项

probeClass、methodCount、resourceBoundary、productionPath（没有直接对象时明确test-only）、classification（ISOLATED_COVERAGE / MANUAL_LIVE / TEST_ONLY_DEMO）、defaultTests、missingCoverage、limitations。一个探针可同时有生产补测和保留联调说明，不能把替身等价为外部质量。

## 许可与运行

RAG_LIVE_TESTS为既有手工许可条件；必要key/pdf仅资源前置，不是授权。默认false或未设置时拒绝启动联调；没有实际授权禁止启用。结果区分PASS/FAIL/ENV_BLOCKED/NOT_RUN与JUnit skipped，不新增运行服务。

## 验收记录

本轮命令/时间、退出码、Java与release、HEAD及未提交源码摘要、XML类/方法数与跳过原因、实际资源类型。归档写模块target的独立新目录，不覆盖OBS/LAB/历史记录；不输出配置凭据或业务正文。无需新collector脚本，复用原有证据方法或最小安全命令记录。
