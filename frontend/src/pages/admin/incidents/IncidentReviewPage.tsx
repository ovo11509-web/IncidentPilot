import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import {
  collectReview,
  reviewMarkdown,
  reviewOpenItems,
  actionDesiredState,
  reviewStages,
  type Review,
  type ReviewProgress
} from "@/lib/incidentReview";
import { stateLabel } from "@/lib/workbenchView";
import { runbookLink } from "@/lib/runbookView";
import { incidentService } from "@/services/incidentService";

export function IncidentReviewPage() {
  const { incidentId = "" } = useParams();
  return <ReviewPage key={incidentId} incidentId={incidentId} />;
}
function ReviewPage({ incidentId }: { incidentId: string }) {
  const [report, setReport] = useState<Review>(),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [revision, setRevision] = useState(0);
  const request = useRef<AbortController>();
  const [progress, setProgress] = useState<ReviewProgress>();
  useEffect(() => {
    const c = new AbortController();
    request.current = c;
    setBusy(true);
    setError("");
    setReport(undefined);
    setProgress(undefined);
    collectReview(incidentService, incidentId, c.signal, undefined, (p) => {
      if (!c.signal.aborted) setProgress(p);
    })
      .then((r) => {
        if (!c.signal.aborted) setReport(r);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError((e as Error).message);
      })
      .finally(() => {
        if (!c.signal.aborted) setBusy(false);
      });
    return () => c.abort();
  }, [incidentId, revision]);
  function download() {
    if (!report || busy || report.issues.length) return;
    try {
      const url = URL.createObjectURL(
        new Blob([reviewMarkdown(report)], { type: "text/markdown;charset=utf-8" })
      );
      const a = document.createElement("a");
      a.href = url;
      a.download = `IncidentPilot-incident-${incidentId}.md`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <div className="incidentpilot-page min-w-0 space-y-6 break-words">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <Link to={`/admin/incidents/${incidentId}`}>← 返回事件详情</Link>
          <p className="ip-eyebrow mt-4">INCIDENT REVIEW</p>
          <h1 className="mt-2 text-2xl font-semibold">事件复盘报告</h1>
          <p className="ip-muted mt-2 break-all">Incident {incidentId}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" disabled={busy} onClick={() => setRevision((v) => v + 1)}>
            {busy ? "采集中…" : "重新采集"}
          </Button>
          {busy && (
            <Button
              variant="outline"
              onClick={() => {
                request.current?.abort();
                setBusy(false);
                setError("已取消采集，请重新采集。");
              }}
            >
              取消
            </Button>
          )}
          <Button
            disabled={busy || !report || !!report.issues.length || !!error}
            onClick={download}
          >
            下载 Markdown
          </Button>
        </div>
      </div>
      <p className="ip-panel ip-muted text-sm">
        只读事实汇编 ·
        候选解释不等于确认根因，模拟动作成功不等于事件已解决。报告来自多次查询，非事务快照。
      </p>
      {busy && (
        <p role="status">
          正在采集：{progress ? reviewStages[progress.stage] : "准备中"} · 本阶段已读取{" "}
          {progress?.count || 0} 条。跨请求复核仍需完成。
        </p>
      )}
      {error && (
        <p role="alert" className="ip-panel ip-amber">
          {error}
        </p>
      )}
      {report && (
        <>
          <section className="ip-panel space-y-3">
            <h2 className="text-lg font-semibold">事件概况</h2>
            <div className="grid gap-3 sm:grid-cols-3">
              <p>
                事件状态 <strong>{stateLabel(report.incident.lifecycleState)}</strong>
              </p>
              <p>
                告警 {report.alerts.length} / {report.incident.alertCount}
              </p>
              <p>
                诊断 {report.diagnoses.length} · 动作 {report.actions.length}
              </p>
            </div>
            <p className="ip-muted break-all text-xs">
              采集开始 {report.startedAt}
              <br />
              采集结束 {report.finishedAt}
              <br />
              事件版本 {report.incident.version} · 首尾复核不能保证原子一致性
            </p>
            {report.issues.map((x) => (
              <p role="alert" className="ip-amber" key={x}>
                {x}（导出已禁用）
              </p>
            ))}
          </section>
          <section className="ip-panel space-y-3">
            <h2 className="text-lg font-semibold">未解决事项与证据限制</h2>
            {reviewOpenItems(report).map((x, i) => (
              <p className="ip-amber text-sm" key={i}>
                {x}
              </p>
            ))}
            {!reviewOpenItems(report).length && (
              <p className="ip-muted">未发现上述状态类待办；根因与生产恢复仍需人工确认。</p>
            )}
          </section>
          <section className="ip-panel space-y-3">
            <h2 className="text-lg font-semibold">告警事实</h2>
            {!report.alerts.length && <p className="ip-muted">暂无关联告警。</p>}
            {report.alerts.map((a) => (
              <article key={a.alertId} className="rounded-lg border p-4 text-sm">
                <h3 className="font-medium">{a.title}</h3>
                <p className="ip-muted break-all">
                  {a.alertId} · {a.severity} · {a.service} / {a.environment}
                </p>
                <p className="ip-muted break-all text-xs">
                  来源时间 {a.occurredAt} · 接收 {a.receivedAt}
                </p>
              </article>
            ))}
          </section>
          <section className="ip-panel space-y-4">
            <h2 className="text-lg font-semibold">诊断建议与历史引用 · 根因未确认</h2>
            {!report.diagnoses.length && <p className="ip-muted">暂无诊断记录。</p>}
            {report.diagnoses.map((d) => (
              <article key={d.diagnosisId} className="space-y-3 rounded-lg border p-4">
                <h3 className="break-all font-medium">诊断 {d.diagnosisId}</h3>
                <p className="ip-muted text-sm">
                  {d.requestState} / {d.outcome || "未完成"} · 当前适用性 {d.evidenceApplicability}{" "}
                  · {d.stale ? "已失效" : "未标记失效"}
                </p>
                {d.snapshot?.truncated === true && (
                  <p className="ip-amber text-sm">当时诊断来源快照有截断。</p>
                )}
                {d.deadlineExceeded && (
                  <p className="ip-amber text-sm">
                    诊断已超时 · 截止 {d.deadlineAt}，请返回事件详情重新诊断。
                  </p>
                )}
                {d.result?.claims?.map((c) => (
                  <p key={c.claimId} className="text-sm">
                    候选：{c.statement}
                    <span className="ip-muted block text-xs">
                      {c.uncertainty} · 引用 {c.citationIds.join("、")}
                    </span>
                  </p>
                ))}
                {d.result?.recommendations?.map((r) => (
                  <p className="text-sm" key={r.recommendationId}>
                    建议 {r.recommendationId}：
                    {typeof r.statement === "string" ? r.statement : "未记录文本"}
                  </p>
                ))}
                {d.result?.evidence?.map((e) => (
                  <details key={e.citationId} className="rounded border p-3">
                    <summary className="cursor-pointer break-all text-sm">
                      引用 {e.citationId} · {e.runbookId} / {e.runbookVersion} · 行 {e.lineStart}–
                      {e.lineEnd}
                    </summary>
                    <pre className="mt-3 whitespace-pre-wrap break-all text-xs">{e.quote}</pre>
                    <p className="ip-muted mt-2 break-all text-xs">
                      历史文档摘要 {e.documentDigest}
                    </p>
                    <Link className="mt-3 inline-block text-sm" to={runbookLink(e)}>
                      核对同版本手册 →
                    </Link>
                  </details>
                ))}
              </article>
            ))}
          </section>
          <section className="ip-panel space-y-3">
            <h2 className="text-lg font-semibold">人工审批与执行结果</h2>
            {!report.actions.length && <p className="ip-muted">暂无处置动作。</p>}
            {report.actions.map((a) => (
              <article key={a.actionId} className="space-y-2 rounded-lg border p-4 text-sm">
                <Link
                  className="break-all font-medium"
                  to={`/admin/incidents/${incidentId}?actionId=${a.actionId}#actions`}
                >
                  动作 {a.actionId} · {stateLabel(a.state)}
                </Link>
                <p>
                  审批 {a.approval.status} · {a.approval.decidedAt || "尚无决定时间"}
                </p>
                <p className="break-all">
                  目标 {a.targetType} / {a.targetId} · 目标状态 {actionDesiredState(a)}
                </p>
                {a.diagnosisSource && (
                  <p className="ip-muted break-all">
                    来源诊断 {a.diagnosisSource.diagnosisId} / {a.diagnosisSource.recommendationId}
                  </p>
                )}
                {a.state === "UNKNOWN" && (
                  <p className="ip-amber">执行结果未知；复盘不能确认成功，不得自动重试。</p>
                )}
                {a.receipts?.map((r) => (
                  <p className="ip-muted break-all" key={r.receiptId}>
                    回执 {r.resultStatus} / {r.resultCode} · {r.disposition} · 已应用{" "}
                    {r.summaryAvailable ? String(r.summary?.applied) : "无法读取"}
                  </p>
                ))}
              </article>
            ))}
          </section>
          <section className="ip-panel space-y-3">
            <h2 className="text-lg font-semibold">时间线 · 只追加事实</h2>
            {!report.timeline.length && <p className="ip-muted">暂无时间线事实。</p>}
            {report.timeline.map((t) => (
              <p className="break-all border-l-2 pl-3 text-sm" key={t.eventId}>
                #{t.sequenceNo} {t.eventType}
                <span className="ip-muted block text-xs">
                  {t.occurredAt} · {t.relatedEntityType} {t.relatedEntityId}
                </span>
              </p>
            ))}
          </section>
        </>
      )}
    </div>
  );
}
