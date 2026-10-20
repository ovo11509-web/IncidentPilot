# 门禁记录模型

仅文件记录，不新增业务表或Schema；2026-10-06完整方案已批准，记录模型已实现，验收状态见quickstart。

## GateRun

schemaVersion、UUID runId、scope、head、sourceDigest、toolVersions、startedAt/finishedAt、state。CHECKING→RUNNING→PASS/FAIL/BLOCKED；仅检查成功为CHECKED；中断为INCOMPLETE或缺少最终记录，不推断PASS。运行对应多个StageResult，新目录禁止覆盖。

## StageResult

stage、固定命令标识、exitCode、适用的tests/passed/failures/errors/skipped、允许跳过类及固定reasonCode。未执行阶段不可填零冒充通过；lint/build无伪造JUnit计数。tests=passed+failures+errors+skipped；异常/重复类/缺失目标拒绝通过。

## EvidenceIndex

受控相对源码路径/哈希、无凭据固定命令、局部诊断相对路径/哈希。safe只含runId/scope/state/time/head/digest/version/counts/受控跳过类/固定reasonCode，不含环境值、配置、XML正文、Exception消息、绝对机器路径。raw/reports/proof仅本地排错；有索引不代表可以公开。
