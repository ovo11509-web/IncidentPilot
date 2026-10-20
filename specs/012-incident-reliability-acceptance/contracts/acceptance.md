# 专项验收契约

1. 指纹按既有六字段，receivedAt相邻差≤实际窗口；封存边界等时刻属于封存前。预期不调用生产partitioner计算。
2. 同source/externalId同payload唯一受理；不同payload为409；故障前确认202与RECEIVED已提交。
3. 新PID/原库/不重投→PROCESSED且关联/Timeline/Outbox事实唯一；未解决不自动变RESOLVED。
4. 暂停本轮容器必须inspect确认Paused，恢复须确认false，finally仅恢复自身。请求等待有上限；不可用不得202/200假受理。
5. 性能四组固定；HTTP峰值并发与任务数分列；所有样本、失败、超时保留，轮询误差声明。
6. H负责危险环境拒绝与外部key屏蔽，新增必需类无跳过；当前来源变化后重新Backend，不拼接历史证据。
7. 归档缺失、空样本、故障未注入、身份不匹配或副作用违规均失败；UNKNOWN无证据保留符合安全契约，可恢复性单列。
