import { id, isoTime, safeInteger, incidentStates } from "./incidentView.js";
import type { Workbench, Action, Diagnosis } from "../types/incident";

export const stateLabels: Record<string, string> = {
  OPEN: "待响应",
  ACKNOWLEDGED: "已确认",
  MITIGATING: "处置中",
  RESOLVED: "已解决",
  CLOSED: "已关闭",
  PENDING_APPROVAL: "待审批",
  APPROVED: "已批准",
  APPROVAL_REJECTED: "已拒绝",
  DISPATCHING: "派发中",
  EXECUTING: "执行中",
  SUCCEEDED: "执行成功",
  FAILED: "执行失败",
  EXECUTION_REJECTED: "执行被拒绝",
  EXPIRED: "已过期",
  UNKNOWN: "结果未知"
};
export function stateLabel(state: string) {
  return stateLabels[state] || state;
}
export function displayTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(new Date(isoTime(value)));
}
function count(value: unknown) {
  if (typeof value !== "number") throw new Error("首页计数格式错误");
  return safeInteger(value);
}
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new Error("首页数据格式错误");
  return value as Record<string, unknown>;
}
function list(value: unknown, limit: number) {
  if (!Array.isArray(value) || value.length > limit) throw new Error("首页列表格式错误");
  return value.map(object);
}
export function validateWorkbench(value: unknown): Workbench {
  const data = object(value),
    counts = object(data.counts);
  const observedAt = isoTime(data.observedAt),
    windowStart = isoTime(data.windowStart),
    windowEnd = isoTime(data.windowEnd);
  if (
    Date.parse(windowEnd) !== Date.parse(observedAt) ||
    Date.parse(windowEnd) - Date.parse(windowStart) !== 86400000
  )
    throw new Error("首页时间窗口错误");
  const alertBuckets = list(data.alertBuckets, 24).map((b, index) => {
    const startAt = isoTime(b.startAt),
      endAt = isoTime(b.endAt);
    if (
      Date.parse(startAt) !== Date.parse(windowStart) + index * 3600000 ||
      Date.parse(endAt) - Date.parse(startAt) !== 3600000
    )
      throw new Error("告警分桶时间错误");
    return { startAt, endAt, count: count(b.count) };
  });
  if (alertBuckets.length !== 24) throw new Error("告警分桶缺失");
  return {
    observedAt,
    windowStart,
    windowEnd,
    counts: {
      activeIncidents: count(counts.activeIncidents),
      pendingApprovalActions: count(counts.pendingApprovalActions),
      executingActions: count(counts.executingActions),
      unknownActions: count(counts.unknownActions)
    },
    alertBuckets,
    attentionIncidents: list(data.attentionIncidents, 8).map((i) => {
      if (typeof i.lifecycleState !== "string" || !incidentStates.includes(i.lifecycleState))
        throw new Error("事件状态无效");
      return {
        incidentId: id(i.incidentId),
        lifecycleState: i.lifecycleState,
        version: count(i.version),
        alertCount: count(i.alertCount),
        lastAlertReceivedAt: isoTime(i.lastAlertReceivedAt),
        updatedAt: isoTime(i.updatedAt),
        pendingApprovalActionCount: count(i.pendingApprovalActionCount),
        executingActionCount: count(i.executingActionCount),
        unknownActionCount: count(i.unknownActionCount)
      };
    }),
    pendingActions: list(data.pendingActions, 8).map((a) => {
      if (a.state !== "PENDING_APPROVAL") throw new Error("待审批状态无效");
      return {
        actionId: id(a.actionId),
        incidentId: id(a.incidentId),
        state: "PENDING_APPROVAL" as const,
        createdAt: isoTime(a.createdAt),
        approvalDueAt: isoTime(a.approvalDueAt)
      };
    }),
    recentActivity: list(data.recentActivity, 8).map((a) => {
      if (
        typeof a.eventType !== "string" ||
        !/^[A-Z][A-Z_]{0,63}$/.test(a.eventType) ||
        count(a.sequenceNo) < 1
      )
        throw new Error("时间线事实无效");
      return {
        eventId: id(a.eventId),
        incidentId: id(a.incidentId),
        sequenceNo: count(a.sequenceNo),
        eventType: a.eventType,
        createdAt: isoTime(a.createdAt)
      };
    })
  };
}
/** Only the explicitly selected action and its linked diagnosis can describe this flow. */
export function selectedFlow(action: Action | undefined, diagnosis: Diagnosis | undefined) {
  if (!action)
    return {
      diagnosis: "未选择动作，无法关联诊断",
      approval: "未选择动作",
      execution: "未选择动作"
    };
  const linked =
    diagnosis &&
    action.diagnosisSource?.diagnosisId === diagnosis.diagnosisId &&
    diagnosis.incidentId === action.incidentId;
  const diagnosisText = !action.diagnosisSource
    ? "人工提案，无诊断来源"
    : !linked
      ? "诊断来源尚未读取"
      : diagnosis.stale || diagnosis.deadlineExceeded || diagnosis.evidenceApplicability !== "VALID"
        ? "诊断证据已失效或待复核"
        : diagnosis.requestState === "COMPLETED"
          ? "已完成关联诊断"
          : "关联诊断尚未完成";
  return {
    diagnosis: diagnosisText,
    approval: stateLabel(
      action.approval.status === "PENDING"
        ? "PENDING_APPROVAL"
        : action.approval.status === "REJECTED"
          ? "APPROVAL_REJECTED"
          : action.approval.status === "APPROVED"
            ? "APPROVED"
            : action.approval.status
    ),
    execution:
      action.state === "UNKNOWN"
        ? "结果未知，请核对可信证据"
        : ["PENDING_APPROVAL", "APPROVED", "APPROVAL_REJECTED", "EXPIRED"].includes(action.state)
          ? "尚无执行结果"
          : stateLabel(action.state)
  };
}
