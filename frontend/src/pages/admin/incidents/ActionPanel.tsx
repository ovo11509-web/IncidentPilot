import { useState } from "react";
import { Button } from "@/components/ui/button";
import { canApprove, IncidentError } from "@/lib/incidentView";
import { incidentService } from "@/services/incidentService";
import type { Action } from "@/types/incident";

export function ActionPanel({actions,refresh,signal,viewSource,selectedActionId}:{actions:Action[];refresh:()=>Promise<void>;signal:AbortSignal;viewSource:(id:string)=>void;selectedActionId?:string}) {
  const [busy,setBusy]=useState(false);const [error,setError]=useState("");const [reason,setReason]=useState<Record<string,string>>({});
  async function decide(a:Action,decision:"APPROVE"|"REJECT") {
    if(busy)return;setBusy(true);setError("");
    try {await incidentService.decision(a.actionId,decision,a.version,reason[a.actionId] || "",signal);await incidentService.action(a.actionId,signal);await refresh();}
    catch(e){if(!signal.aborted){setError((e as Error).message);if(e instanceof IncidentError && e.status===409)await refresh();}}
    finally{if(!signal.aborted)setBusy(false);}
  }
  return <section className="ip-panel space-y-4" id="actions"><h2 className="text-lg font-semibold">人工审批与执行结果</h2><p className="text-sm text-slate-500">冻结绑定来自服务端持久化记录。批准和拒绝是独立操作；执行完成不会自动解决 Incident。</p>
    {error && <p role="alert" className="text-rose-700">{error}</p>}{actions.length===0 && <p className="text-slate-500">暂无 Action。符合条件的建议须由人员显式选择。</p>}
    {actions.map(a=><article key={a.actionId} id={`action-${a.actionId}`} tabIndex={-1} className={`space-y-3 rounded-lg border p-4 ${selectedActionId===a.actionId?"ip-action-selected":""}`}><div className="flex flex-wrap justify-between gap-2"><h3 className="break-all font-semibold">Action {a.actionId}</h3><span className="rounded bg-slate-100 px-2 py-1 text-sm">{a.state} · v{a.version}</span></div>
      <dl className="grid gap-2 text-sm sm:grid-cols-2"><div>工具：{a.toolName} v{a.toolVersion}</div><div>冻结目标：{a.targetType} / {a.targetId}</div><div>冻结参数：{a.parametersAvailable?a.parameters?.desiredState:"安全预览不可用"}</div><div>审批：{a.approval.status} · {a.approval.deciderId || "未决定"}</div></dl>
      <p className="break-all font-mono text-xs">绑定摘要 {a.bindingDigest}</p>
      <p className="text-xs">审批截止 {a.approval.approvalDueAt} · 决定时间 {a.approval.decidedAt || "—"} · {a.approval.reasonCode || ""}</p>
      {a.originalActionId && <p className="text-sm">原提案 {a.originalActionId}（本 Action 需重新审批）</p>}
      {a.diagnosisSource && <Button variant="ghost" className="h-auto max-w-full whitespace-normal break-all py-2" onClick={()=>viewSource(a.diagnosisSource!.diagnosisId)}>查看诊断来源 {a.diagnosisSource.diagnosisId} / {a.diagnosisSource.recommendationId}</Button>}
      {a.state==="PENDING_APPROVAL" && <div className="space-y-2 border-t pt-3"><label className="block text-sm">审批理由（拒绝必填）<input aria-label={`审批理由 ${a.actionId}`} className="ml-3 rounded border p-2" maxLength={512} value={reason[a.actionId] || ""} onChange={e=>setReason({...reason,[a.actionId]:e.target.value})}/></label><div className="flex flex-wrap gap-2"><Button disabled={busy || !canApprove(a)} onClick={()=>decide(a,"APPROVE")}>人工批准此冻结 Action</Button><Button variant="outline" disabled={busy || !Number.isSafeInteger(a.version) || !reason[a.actionId]?.trim()} onClick={()=>decide(a,"REJECT")}>拒绝此 Action</Button></div>{!canApprove(a) && <p className="text-amber-700">预览或状态不满足批准条件，批准已禁用。</p>}</div>}
      {a.state==="UNKNOWN" && <p role="status" className="rounded bg-amber-50 p-3 font-medium text-amber-800">UNKNOWN：执行结果未知，不能推定失败或成功。请核对可信证据并手动刷新；没有写重试。</p>}
      {a.execution && <p className="break-all text-sm">执行 {a.execution.executionId} · {a.execution.resultStatus || "尚无权威结果"} / {a.execution.resultCode || "—"} · 票据状态 {a.execution.ticketStatus}</p>}
      {a.receipts?.map(r=><div key={r.receiptId} className="rounded bg-slate-50 p-3 text-sm"><p>回执 {r.receiptId} · {r.resultStatus} / {r.resultCode} · {r.disposition}（采纳状态）</p><p>发生 {r.occurredAt || "—"} · 接受 {r.acceptedAt || "—"}</p><p>{r.summaryAvailable?`desiredState=${r.summary?.desiredState} · applied=${r.summary?.applied===null?"未知":String(r.summary?.applied)} ${r.summary?.proofCode || ""}`:"安全摘要不可用，保留历史状态"}</p></div>)}
    </article>)}
  </section>;
}
