import { id, isoTime, safeInteger } from "./incidentView.js";
import type {
  Action,
  Alert,
  AlertPage,
  Diagnosis,
  DiagnosisPage,
  Incident,
  TimelineFact,
  TimelinePage
} from "../types/incident";

export type Review = {
  incident: Incident;
  alerts: Alert[];
  diagnoses: Diagnosis[];
  actions: Action[];
  timeline: TimelineFact[];
  startedAt: string;
  finishedAt: string;
  issues: string[];
};
type Reader = {
  get: (i: string, s?: AbortSignal) => Promise<Incident>;
  alerts: (i: string, cursor?: string | null, s?: AbortSignal) => Promise<AlertPage>;
  timeline: (i: string, cursor?: number, s?: AbortSignal) => Promise<TimelinePage>;
  diagnoses: (i: string, cursor?: string, s?: AbortSignal) => Promise<DiagnosisPage>;
  diagnosis: (i: string, s?: AbortSignal) => Promise<Diagnosis>;
  actions: (i: string, s?: AbortSignal) => Promise<Action[]>;
};
const limits = { alerts: 1000, diagnoses: 200, timeline: 5000, actions: 200 };
export type ReviewProgress = {
  stage:
    | "incident"
    | "alerts"
    | "timeline"
    | "diagnosisHistory"
    | "diagnoses"
    | "actions"
    | "verify"
    | "complete";
  count: number;
};
export const reviewStages: Record<ReviewProgress["stage"], string> = {
  incident: "事件概况",
  alerts: "告警事实",
  timeline: "时间线",
  diagnoses: "诊断详情",
  diagnosisHistory: "诊断历史目录",
  actions: "审批与执行",
  verify: "首尾复核",
  complete: "采集完成"
};
function text(value: unknown): string {
  if (typeof value !== "string") throw Error("报告文本格式错误");
  return value;
}
function unique<T>(items: T[], key: (item: T) => string) {
  const keys = items.map(key);
  if (new Set(keys).size !== keys.length) throw Error("报告出现重复身份，请刷新");
}
function actionFingerprint(items: Action[], incidentId: string) {
  unique(items, (a) => id(a.actionId));
  return items
    .map((a) => {
      if (a.incidentId !== incidentId) throw Error("动作属于其他事件");
      safeInteger(a.version);
      text(a.state);
      text(a.approval.status);
      return JSON.stringify([a.actionId, a.version, a.state, a.approval, a.execution, a.receipts]);
    })
    .sort()
    .join("\n");
}
function diagnosisFingerprint(items: Diagnosis[], incidentId: string) {
  unique(items, (d) => id(d.diagnosisId));
  return items
    .map((d) => {
      if (d.incidentId !== incidentId) throw Error("诊断属于其他事件");
      return JSON.stringify([
        d.diagnosisId,
        d.requestState,
        d.outcome,
        d.resultDigest,
        d.stale,
        d.evidenceApplicability,
        d.deadlineExceeded
      ]);
    })
    .sort()
    .join("\n");
}
function validateDiagnosisDetail(d: Diagnosis) {
  if (typeof d.stale !== "boolean" || typeof d.deadlineExceeded !== "boolean")
    throw Error("诊断标记格式错误");
  text(d.requestState);
  text(d.evidenceApplicability);
  const result = d.result;
  if (!result) return;
  for (const key of [
    "claims",
    "evidence",
    "recommendations",
    "missingFacts",
    "limitations"
  ] as const)
    if (result[key] !== undefined && (!Array.isArray(result[key]) || result[key]!.length > 1000))
      throw Error("诊断结果列表格式错误");
  for (const c of result.claims || []) {
    text(c.claimId);
    text(c.statement);
    text(c.uncertainty);
    if (!Array.isArray(c.citationIds)) throw Error("引用身份格式错误");
    c.citationIds.forEach(text);
  }
  for (const e of result.evidence || []) {
    for (const key of [
      "citationId",
      "runbookId",
      "runbookVersion",
      "sectionId",
      "documentDigest",
      "chunkDigest",
      "quote"
    ] as const)
      text(e[key]);
    if (
      safeInteger(e.lineStart) < 1 ||
      safeInteger(e.lineEnd) < e.lineStart ||
      !/^[a-f0-9]{64}$/.test(e.documentDigest) ||
      !/^[a-f0-9]{64}$/.test(e.chunkDigest)
    )
      throw Error("引用行号或摘要格式错误");
  }
  for (const r of result.recommendations || []) text(r.recommendationId);
  for (const x of [...(result.missingFacts || []), ...(result.limitations || [])]) text(x);
}

export async function collectReview(
  reader: Reader,
  incidentId: string,
  signal: AbortSignal,
  cap = limits,
  onProgress?: (progress: ReviewProgress) => void
): Promise<Review> {
  id(incidentId);
  signal.throwIfAborted();
  const startedAt = new Date().toISOString(),
    issues: string[] = [];
  const progress = (stage: ReviewProgress["stage"], count: number) => {
    signal.throwIfAborted();
    onProgress?.({ stage, count });
  };
  progress("incident", 0);
  const incident = await reader.get(incidentId, signal);
  if (incident.incidentId !== incidentId) throw Error("事件身份不匹配");
  safeInteger(incident.version);
  safeInteger(incident.alertCount);
  isoTime(incident.episodeStartAt);
  async function pages<T, C>(
    fetchPage: (cursor: C) => Promise<{ items: T[]; hasMore: boolean; next: C }>,
    initial: C,
    max: number,
    stage?: ReviewProgress["stage"]
  ): Promise<T[]> {
    const items: T[] = [],
      cursors = new Set<C>();
    if (stage) progress(stage, 0);
    let cursor = initial;
    while (items.length < max) {
      signal.throwIfAborted();
      const page = await fetchPage(cursor);
      signal.throwIfAborted();
      if (!Array.isArray(page.items) || typeof page.hasMore !== "boolean")
        throw Error("报告分页格式错误");
      const remaining = max - items.length;
      items.push(...page.items.slice(0, remaining));
      if (stage) progress(stage, items.length);
      if (page.items.length > remaining || (items.length >= max && page.hasMore)) {
        issues.push(`记录超过采集上限 ${max}，请在事件详情继续查看`);
        return items;
      }
      if (!page.hasMore) return items;
      if (!page.items.length || page.next == null || page.next === cursor || cursors.has(page.next))
        throw Error("分页游标未前进，请刷新");
      cursors.add(page.next);
      cursor = page.next;
    }
    throw Error("无效采集上限");
  }
  const alerts = await pages(
    async (c) => {
      const p = await reader.alerts(incidentId, c, signal);
      if (p.hasMore) id(p.nextAfterId);
      return { ...p, next: p.nextAfterId };
    },
    null as string | null,
    cap.alerts,
    "alerts"
  );
  unique(alerts, (a) => id(a.alertId));
  for (const a of alerts) {
    isoTime(a.occurredAt);
    isoTime(a.receivedAt);
    for (const k of [
      "title",
      "sourceId",
      "ruleId",
      "severity",
      "service",
      "environment",
      "resourceType",
      "resourceId"
    ] as const)
      text(a[k]);
  }
  const timeline = await pages(
    async (c) => {
      const p = await reader.timeline(incidentId, c, signal);
      safeInteger(p.nextAfterSequence);
      if (p.hasMore && p.nextAfterSequence <= c) throw Error("时间线游标未前进");
      return { ...p, next: p.nextAfterSequence };
    },
    0,
    cap.timeline,
    "timeline"
  );
  unique(timeline, (t) => id(t.eventId));
  let sequence = 0;
  for (const t of timeline) {
    if (safeInteger(t.sequenceNo) <= sequence) throw Error("时间线序号未递增");
    sequence = t.sequenceNo;
    isoTime(t.occurredAt);
    text(t.eventType);
  }
  async function history(stage?: ReviewProgress["stage"]) {
    return pages(
      async (c) => {
        const p = await reader.diagnoses(incidentId, c, signal);
        if (p.hasMore) id(p.nextAfterId);
        return { ...p, next: p.nextAfterId };
      },
      undefined as string | undefined,
      cap.diagnoses,
      stage
    );
  }
  const initialHistory = await history("diagnosisHistory");
  diagnosisFingerprint(initialHistory, incidentId);
  const diagnoses: Diagnosis[] = [];
  progress("diagnoses", 0);
  for (const d of initialHistory) {
    signal.throwIfAborted();
    const detail = await reader.diagnosis(d.diagnosisId, signal);
    if (detail.diagnosisId !== d.diagnosisId) throw Error("诊断身份不匹配");
    validateDiagnosisDetail(detail);
    diagnoses.push(detail);
    progress("diagnoses", diagnoses.length);
  }
  const diagnosisBefore = diagnosisFingerprint(diagnoses, incidentId);
  if (diagnosisBefore !== diagnosisFingerprint(initialHistory, incidentId))
    issues.push("读取诊断详情时记录已变化，请重新采集");
  // shortcut: legacy action reads are unpaged; add server pagination before supporting large incidents.
  const allActions = await reader.actions(incidentId, signal);
  if (allActions.length > cap.actions) issues.push(`动作超过采集上限 ${cap.actions}`);
  const actions = allActions.slice(0, cap.actions),
    actionBefore = actionFingerprint(actions, incidentId);
  progress("actions", actions.length);
  progress("verify", 0);
  const after = await reader.get(incidentId, signal);
  if (
    after.incidentId !== incidentId ||
    after.version !== incident.version ||
    after.alertCount !== incident.alertCount ||
    after.lifecycleState !== incident.lifecycleState
  )
    issues.push("采集期间事件已变化，请刷新");
  if (alerts.length !== incident.alertCount) issues.push("告警采集数量与事件记录不符，请刷新");
  const finalActions = await reader.actions(incidentId, signal);
  if (
    finalActions.length > cap.actions ||
    actionBefore !== actionFingerprint(finalActions, incidentId)
  )
    issues.push("采集期间动作或回执已变化，请刷新");
  if (diagnosisBefore !== diagnosisFingerprint(await history(), incidentId))
    issues.push("采集期间诊断已变化，请刷新");
  const tail = await reader.timeline(incidentId, sequence, signal);
  if (tail.items.length || tail.hasMore) issues.push("采集期间新增时间线事实，请刷新");
  signal.throwIfAborted();
  progress("complete", alerts.length + timeline.length + diagnoses.length + actions.length);
  return {
    incident,
    alerts,
    diagnoses,
    actions,
    timeline,
    startedAt,
    finishedAt: new Date().toISOString(),
    issues: [...new Set(issues)]
  };
}

export function reviewOpenItems(r: Review): string[] {
  const out: string[] = [];
  if (!["RESOLVED", "CLOSED"].includes(r.incident.lifecycleState))
    out.push("事件尚未解决；动作成功不代表事件已解决。");
  if (!r.diagnoses.length) out.push("尚无诊断记录，根因未确认。");
  for (const d of r.diagnoses) {
    if (d.stale || d.evidenceApplicability !== "VALID")
      out.push(`诊断 ${d.diagnosisId} 为历史证据，当前适用性 ${d.evidenceApplicability}。`);
    if (d.requestState === "RUNNING")
      out.push(
        d.deadlineExceeded
          ? `诊断 ${d.diagnosisId} 已超时（截止 ${d.deadlineAt}），请返回事件详情重新诊断。`
          : `诊断 ${d.diagnosisId} 尚未完成。`
      );
    if (d.snapshot?.truncated === true) out.push(`诊断 ${d.diagnosisId} 当时的来源快照有截断。`);
    for (const x of [...(d.result?.missingFacts || []), ...(d.result?.limitations || [])])
      out.push(text(x));
  }
  for (const a of r.actions)
    if (
      [
        "UNKNOWN",
        "PENDING_APPROVAL",
        "APPROVED",
        "DISPATCHING",
        "EXECUTING",
        "FAILED",
        "EXECUTION_REJECTED"
      ].includes(a.state)
    )
      out.push(
        `动作 ${a.actionId}：${a.state}${a.state === "UNKNOWN" ? "，结果未知，不得自动重试。" : "。"}`
      );
  return [...new Set(out)];
}
// Escape untrusted text; export a field allowlist, never entire stored objects.
function md(value: unknown) {
  return String(value ?? "未记录")
    .replace(/[\r\n]+/g, " ")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/([\\`*_{}[\]()#+.!|~-])/g, "\\$1");
}
export function reviewMarkdown(r: Review): string {
  if (r.issues.length) throw Error("记录不完整或已变化，不能导出");
  const lines = [
    `# IncidentPilot 事件复盘 ${r.incident.incidentId}`,
    "",
    `采集范围：${r.startedAt} 至 ${r.finishedAt}`,
    "跨请求事实汇编，非事务快照；首尾复核未检测到变化不代表原子一致性。",
    "候选解释不是确认根因；模拟动作成功不代表事件已解决。",
    "",
    `事件状态：${md(r.incident.lifecycleState)}；版本：${r.incident.version}；告警数：${r.alerts.length}`,
    "",
    "## 告警事实"
  ];
  for (const a of r.alerts)
    lines.push(
      `- ${a.alertId} · ${md(a.title)} · ${md(a.severity)} · ${md(a.service)} / ${md(a.environment)} · 来源 ${a.occurredAt} · 接收 ${a.receivedAt}`
    );
  lines.push("", "## 诊断与历史证据（根因未确认）");
  for (const d of r.diagnoses) {
    lines.push(
      `### 诊断 ${d.diagnosisId}`,
      `${md(d.requestState)} / ${md(d.outcome)}；历史适用性 ${md(d.evidenceApplicability)}；失效 ${d.stale ? "是" : "否"}；当时快照截断 ${d.snapshot?.truncated === true ? "是" : "否"}`
    );
    if (d.deadlineExceeded)
      lines.push(`诊断已超时；截止 ${md(d.deadlineAt)}，请返回事件详情重新诊断。`);
    for (const c of d.result?.claims || [])
      lines.push(
        `- 候选：${md(c.statement)}；不确定性 ${md(c.uncertainty)}；引用 ${md(c.citationIds.join("、"))}`
      );
    for (const e of d.result?.evidence || []) {
      lines.push(
        `- 引用 ${md(e.citationId)}：${md(e.runbookId)} / ${md(e.runbookVersion)} / ${md(e.sectionId)}；行 ${e.lineStart}–${e.lineEnd}；文档摘要 ${md(e.documentDigest)}；章节摘要 ${md(e.chunkDigest)}`
      );
      for (const line of text(e.quote).split(/\r?\n/)) lines.push(`> ${md(line)}`);
    }
    for (const rec of d.result?.recommendations || [])
      lines.push(
        `- 建议 ${md(rec.recommendationId)}：${md(typeof rec.statement === "string" ? rec.statement : "文本未记录")}`
      );
  }
  lines.push("", "## 人工审批与执行结果");
  for (const a of r.actions) {
    lines.push(
      `- 动作 ${a.actionId}：${md(a.state)}；工具 ${md(a.toolName)}；审批 ${md(a.approval.status)}；决定时间 ${md(a.approval.decidedAt)}；原因 ${md(a.approval.reasonCode)}`
    );
    lines.push(
      `  目标 ${md(a.targetType)} / ${md(a.targetId)}；目标状态 ${md(actionDesiredState(a))}`
    );
    if (a.diagnosisSource)
      lines.push(
        `  来源诊断 ${a.diagnosisSource.diagnosisId} / 建议 ${md(a.diagnosisSource.recommendationId)}`
      );
    for (const receipt of a.receipts || [])
      lines.push(
        `  回执 ${md(receipt.receiptId)}：${md(receipt.resultStatus)} / ${md(receipt.resultCode)} / ${md(receipt.disposition)}；已应用 ${receipt.summaryAvailable ? md(receipt.summary?.applied) : "无法读取"}`
      );
  }
  lines.push("", "## 时间线事实");
  for (const t of r.timeline)
    lines.push(
      `- #${t.sequenceNo} ${md(t.eventType)} · ${t.occurredAt} · 关联 ${md(t.relatedEntityType)} / ${md(t.relatedEntityId)}`
    );
  lines.push(
    "",
    "## 未解决事项",
    ...reviewOpenItems(r).map((x) => `- ${md(x)}`),
    "",
    "本报告为本地只读导出，不会签署、归档或改变事件状态。",
    "IncidentPilot 衍生自 nageoffer/ragent，保留 Apache-2.0 及上游署名。",
    ""
  );
  return lines.join("\n");
}
export function actionDesiredState(a: Action): string {
  return a.parametersAvailable && ["READY", "PAUSED"].includes(a.parameters?.desiredState || "")
    ? a.parameters!.desiredState
    : "参数不可读取";
}
