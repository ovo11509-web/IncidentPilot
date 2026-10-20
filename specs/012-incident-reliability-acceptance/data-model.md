# 验收数据模型

不新增业务表。JSON案例记录caseId、offsetMicros、keyGroup、failedIds、sealAtMicros及expectedAnchorByAlert；预期为人工依据契约写定。

案例结果分列caseId/orderSeed/输入数/PROCESSED/FAILED/未结/关联/事件；成功成员按预期anchor对账，失败未关联不误判丢失。

HTTP结果记录请求尝试数、实际峰值活跃请求、状态码计数、唯一身份及持久化事实；不保存认证头/密钥。

基线分组：mode(hot/multi)、history(0/500)、sent/accepted/processed/failed/unfinished、接收及观察完成延迟、批次耗时、完成吞吐。百分位是完成子集，失败/超时单列，不能当整体SLO。

来源包含lab.runId/sourceDigest；原始日志私有，仅白名单事实报告用于审阅。固定种子重复不满足总体独立同分布假设，不输出概率保证。
