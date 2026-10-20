import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { runbookLink } from "@/lib/runbookView";
import { Button } from "@/components/ui/button";
import { incidentService } from "@/services/incidentService";
import { canSelect, incidentStates, IncidentError, rememberIntent, safeInteger } from "@/lib/incidentView";
import type { Action, Diagnosis, DiagnosisPage, Incident, Recommendation, SelectIntent } from "@/types/incident";

type Props = {incident:Incident; history:DiagnosisPage; actions:Action[]; revision:number; refresh:()=>Promise<void>; more:()=>Promise<void>; signal:AbortSignal; sourceId?:string};
export function DiagnosisPanel({incident,history,actions,revision,refresh,more,signal,sourceId}:Props) {
  const [selected,setSelected] = useState<string>();
  const [detail,setDetail] = useState<Diagnosis>();
  const [error,setError] = useState("");
  const [busy,setBusy] = useState(false);
  const diagnosisIntent = useRef<{sourceRequestId:string;expectedIncidentVersion:number}>();
  const selectIntents = useRef(new Map<string,SelectIntent>());
  useEffect(()=>{if(sourceId)setSelected(sourceId);},[sourceId]);
  useEffect(()=>{
    if(!selected){setDetail(undefined);return;}
    const c = new AbortController();const abort=()=>c.abort();signal.addEventListener("abort",abort);
    setDetail(undefined);
    incidentService.diagnosis(selected,c.signal).then(value=>{if(!c.signal.aborted)setDetail(value);}).catch(e=>{if(!c.signal.aborted)setError(e.message);});
    return ()=>{c.abort();signal.removeEventListener("abort",abort);};
  },[selected,revision,signal]);
  async function diagnose() {
    if(busy)return;setBusy(true);setError("");
    try {
      if(!diagnosisIntent.current) {const current=await incidentService.get(incident.incidentId,signal);diagnosisIntent.current={sourceRequestId:crypto.randomUUID(),expectedIncidentVersion:safeInteger(current.version)};}
      const d = await incidentService.diagnose(incident.incidentId,diagnosisIntent.current,signal);diagnosisIntent.current=undefined;
      await refresh();if(!signal.aborted)setSelected(d.diagnosisId);
    }catch(e){if(!signal.aborted){setError((e as Error).message);if(e instanceof IncidentError && e.status!==0){diagnosisIntent.current=undefined;if(e.status===409)await refresh();}}}
    finally{if(!signal.aborted)setBusy(false);}
  }
  async function choose(r:Recommendation,originalActionId?:string) {
    if(!detail || busy)return;setBusy(true);setError("");
    const key=`${detail.diagnosisId}:${r.recommendationId}:${originalActionId || "first"}`;
    try {
      const intent=rememberIntent(selectIntents.current,key,()=>({sourceRequestId:crypto.randomUUID(),expectedIncidentVersion:safeInteger(incident.version),diagnosisDigest:detail.resultDigest || "",recommendationDigest:r.recommendationDigest,...(originalActionId?{originalActionId}:{})}));
      const a=await incidentService.select(detail.diagnosisId,r.recommendationId,intent,signal);
      await incidentService.action(a.actionId,signal);await refresh();
    }catch(e){if(!signal.aborted){setError((e as Error).message);if(e instanceof IncidentError && e.status!==0){selectIntents.current.delete(key);if(e.status===409)await refresh();}}}
    finally{if(!signal.aborted)setBusy(false);}
  }
  const versionSafe=Number.isSafeInteger(incident.version) && incident.version>=0;
  return <section className="ip-panel space-y-4" id="diagnosis">
    <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">诊断与证据</h2><Button onClick={diagnose} disabled={busy || !versionSafe || !incidentStates.includes(incident.lifecycleState) || incident.lifecycleState==="CLOSED"}>{busy?"提交中…":diagnosisIntent.current?"手动重发原诊断请求":"发起新诊断"}</Button></div>
    <p className="text-sm text-slate-500">候选解释不代表根因确认。诊断只读；选择建议后仍需独立人工审批。</p>
    {!versionSafe && <p role="alert">版本超出安全整数范围，命令已禁用。</p>}
    {error && <p role="alert" className="text-rose-700">{error}</p>}
    {history.items.length===0 && <p className="text-slate-500">暂无诊断。请显式发起诊断。</p>}
    <div className="flex flex-wrap gap-2">{history.items.map(d=><Button key={d.diagnosisId} className="h-auto max-w-full whitespace-normal break-all py-2" variant={selected===d.diagnosisId?"default":"outline"} onClick={()=>{setError("");setSelected(d.diagnosisId);}}>{d.diagnosisId} · {d.requestState} · {d.outcome || "待结果"}{d.stale?" · 已失效":""}</Button>)}</div>
    {history.hasMore && <Button variant="outline" disabled={busy} onClick={more}>更多诊断历史</Button>}
    {selected && !detail && !error && <p>正在读取诊断证据…</p>}
    {detail && <div className="space-y-4">
      <p className="font-medium">{detail.requestState} / {detail.outcome || "尚无结果"} · 当前适用性 {detail.evidenceApplicability}</p>
      {(detail.stale || detail.deadlineExceeded || detail.evidenceApplicability!=="VALID") && <p className="rounded-lg bg-amber-50 p-3 text-amber-800">历史引用可复核，当前建议不可选择。请刷新事实并显式重新诊断。{detail.deadlineExceeded?" 请求已超过截止时间。":""}</p>}
      <details><summary className="cursor-pointer font-medium">冻结来源快照（来源报告）</summary><pre className="mt-2 whitespace-pre-wrap break-all rounded-lg bg-slate-50 p-4 text-xs">{JSON.stringify(detail.snapshot,null,2)}</pre></details>
      {detail.result && <>
        <div className="space-y-2"><h3 className="font-medium">候选解释 · 根因未确认</h3>{detail.result.claims?.map(c=><p key={c.claimId} className="rounded bg-indigo-50 p-3 text-sm">{c.statement}<span className="block text-xs text-slate-500">{c.uncertainty} · 引用 {c.citationIds.join("、")}</span></p>)}</div>
        <div className="grid gap-4 sm:grid-cols-2"><div><h3 className="font-medium">缺失事实</h3>{detail.result.missingFacts?.map((x,i)=><p key={i} className="mt-2 text-sm text-amber-800">{x}</p>)}</div><div><h3 className="font-medium">限制</h3>{detail.result.limitations?.map((x,i)=><p key={i} className="mt-2 text-sm text-slate-600">{x}</p>)}</div></div>
        <div className="space-y-2"><h3 className="font-medium">可复核引用原文</h3>{detail.result.evidence?.map(e=><details key={e.citationId} className="rounded-lg border p-3"><summary className="cursor-pointer text-sm font-medium">{e.citationId} · {e.runbookId} v{e.runbookVersion} · {e.sectionId} · 行 {e.lineStart}–{e.lineEnd}</summary><p className="mt-2 break-all font-mono text-xs text-slate-500">文档 SHA-256 {e.documentDigest}<br/>片段 SHA-256 {e.chunkDigest}</p><pre className="mt-2 whitespace-pre-wrap break-all rounded bg-slate-50 p-3 text-sm">{e.quote}</pre><Link className="mt-3 inline-block text-sm" to={runbookLink(e)}>核对目录中的同版本手册 →</Link><p className="ip-muted mt-1 text-xs">上方为历史保存原文；目录内容不替代历史证据。</p></details>)}</div>
        <details><summary className="cursor-pointer text-sm">完整历史文档与诊断记录</summary><pre className="mt-2 max-h-80 overflow-auto whitespace-pre-wrap break-all text-xs">{JSON.stringify(detail.result,null,2)}</pre></details>
        {detail.result.recommendations?.map(r=>{
          const rejected=actions.filter(a=>a.state==="APPROVAL_REJECTED" && a.diagnosisSource?.diagnosisId===detail.diagnosisId && a.diagnosisSource.recommendationId===r.recommendationId).slice(-1)[0];
          const allowed=versionSafe && canSelect(detail,r,incident.lifecycleState);
          return <article key={r.recommendationId} className="space-y-2 rounded-lg border p-4"><h3 className="font-medium">建议 {r.recommendationId}</h3><p className="text-sm">{typeof r.statement==="string"?r.statement:"建议文本不可用"}</p><p className="text-xs text-slate-500">{String(r.type)} · {String(r.risk)}</p><details><summary className="cursor-pointer text-xs">建议来源与摘要</summary><pre className="whitespace-pre-wrap break-all text-xs">{JSON.stringify(r,null,2)}</pre></details>
            <Button variant="outline" disabled={busy || !allowed} onClick={()=>choose(r)}>创建 / 重发同一待审批提案</Button>
            {rejected && <Button className="ml-2" variant="outline" disabled={busy || !allowed} onClick={()=>choose(r,rejected.actionId)}>拒绝后明确提出新 Action</Button>}
            {!allowed && <p className="text-sm text-slate-500">当前不可转换；最终资格由服务端重新校验。</p>}
          </article>;
        })}
      </>}
    </div>}
  </section>;
}
