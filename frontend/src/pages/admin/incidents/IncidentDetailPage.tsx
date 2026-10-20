import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { incidentService } from "@/services/incidentService";
import { appendUnique, shouldPoll, serialReads } from "@/lib/incidentView";
import { selectedFlow } from "@/lib/workbenchView";
import type { Action, AlertPage, DiagnosisPage, Incident, TimelinePage } from "@/types/incident";
import { DiagnosisPanel } from "./DiagnosisPanel";
import { ActionPanel } from "./ActionPanel";

export function IncidentDetailPage() { const {incidentId=""}=useParams();return <Detail key={incidentId} incidentId={incidentId}/>; }
function Detail({incidentId}:{incidentId:string}) {
  const [data,setData]=useState<{incident:Incident;alerts:AlertPage;timeline:TimelinePage;history:DiagnosisPage;actions:Action[]}>();
  const [params]=useSearchParams();
  const requestedAction=params.get("actionId") || undefined;
  const [selectedActionId,setSelectedActionId]=useState<string>();
  const focusedAction=useRef<string>();
  useEffect(()=>{setSelectedActionId(requestedAction);},[requestedAction]);
  useEffect(()=>{
    if(requestedAction && data?.actions.some(a=>a.actionId===requestedAction) && focusedAction.current!==requestedAction){
      const element=document.getElementById(`action-${requestedAction}`);
      if(element){element.scrollIntoView();element.focus({preventScroll:true});focusedAction.current=requestedAction;}
    }
  },[data,requestedAction]);
  const selectedAction=data?.actions.find(a=>a.actionId===selectedActionId);
  const flow=selectedFlow(selectedAction,data?.history.items.find(d=>d.diagnosisId===selectedAction?.diagnosisSource?.diagnosisId));
  const [error,setError]=useState("");const [busy,setBusy]=useState(false);const [revision,setRevision]=useState(0);const [source,setSource]=useState<string>();
  const lifetime=useRef(new AbortController());
  const [signal,setSignal]=useState(lifetime.current.signal);
  const read=useRef(serialReads());const request=useRef<AbortController>();
  const load=useCallback(()=>read.current(async()=>{
    if(lifetime.current.signal.aborted)return;
    setBusy(true);setError("");const c=new AbortController();request.current=c;
    try {
      const [incident,alerts,timeline,history,actions]=await Promise.all([
        incidentService.get(incidentId,c.signal),incidentService.alerts(incidentId,null,c.signal),incidentService.timeline(incidentId,0,c.signal),incidentService.diagnoses(incidentId,undefined,c.signal),incidentService.actions(incidentId,c.signal)
      ]);
      if(!c.signal.aborted){setData(old=>({incident,actions,
        alerts:old?{...(old.alerts.hasMore?old.alerts:alerts),items:appendUnique(old.alerts.items,alerts.items,x=>x.alertId)}:alerts,
        timeline:old?{...(old.timeline.hasMore?old.timeline:timeline),items:appendUnique(old.timeline.items,timeline.items,x=>x.eventId).sort((a,b)=>a.sequenceNo-b.sequenceNo)}:timeline,
        history:old?{...(old.history.hasMore?old.history:history),items:appendUnique(history.items,old.history.items.filter(x=>!history.items.some(y=>x.diagnosisId===y.diagnosisId)),x=>x.diagnosisId)}:history}));setRevision(x=>x+1);}
    }catch(e){if(!c.signal.aborted){setError((e as Error).message);setData(undefined);}}
    finally{if(request.current===c){if(!c.signal.aborted)setBusy(false);}}
  }),[incidentId]);
  useEffect(()=>{
    if(lifetime.current.signal.aborted){lifetime.current=new AbortController();setSignal(lifetime.current.signal);}
    load();return ()=>{lifetime.current.abort();request.current?.abort();};
  },[load]);
  useEffect(()=>{
    if(!data || error || !shouldPoll(data.history.items,data.actions))return;
    let timer:ReturnType<typeof setTimeout>;
    const schedule=()=>{clearTimeout(timer);if(!document.hidden)timer=setTimeout(()=>void load(),5000);};
    const visibility=()=>{if(document.hidden)clearTimeout(timer);else schedule();};
    schedule();document.addEventListener("visibilitychange",visibility);
    return ()=>{clearTimeout(timer);document.removeEventListener("visibilitychange",visibility);};
  },[data,error,load]);
  function more(kind:"alerts"|"timeline"|"history") {return read.current(async()=>{
    if(!data || signal.aborted)return;setBusy(true);setError("");
    try {
      if(kind==="alerts") {const page=await incidentService.alerts(incidentId,data.alerts.nextAfterId,signal);if(!signal.aborted)setData(old=>old?{...old,alerts:{...page,items:appendUnique(old.alerts.items,page.items,x=>x.alertId)}}:old);}
      if(kind==="timeline") {const page=await incidentService.timeline(incidentId,data.timeline.nextAfterSequence,signal);if(!signal.aborted)setData(old=>old?{...old,timeline:{...page,items:appendUnique(old.timeline.items,page.items,x=>x.eventId)}}:old);}
      if(kind==="history") {const page=await incidentService.diagnoses(incidentId,data.history.nextAfterId,signal);if(!signal.aborted)setData(old=>old?{...old,history:{...page,items:appendUnique(old.history.items,page.items,x=>x.diagnosisId)}}:old);}
    }catch(e){if(!signal.aborted)setError((e as Error).message);}
    finally{if(!signal.aborted)setBusy(false);}
  });}
  return <div className="incidentpilot-page min-w-0 space-y-6 break-words"><div className="flex flex-wrap justify-between gap-3"><div><Link to="/admin/incidents" className="text-sm text-indigo-600">← 事件列表</Link><h1 className="mt-2 break-all text-2xl font-semibold">Incident {incidentId}</h1></div><Button variant="outline" disabled={busy} onClick={load}>{busy?"读取中…":"手动刷新证据"}</Button></div>
    <Link to={`/admin/incidents/${incidentId}/review`} className="inline-flex rounded-lg border px-4 py-2 text-sm">查看事件复盘报告 →</Link>
    {error && <p role="alert" className="rounded bg-rose-50 p-4 text-rose-700">{error}（轮询已停止）</p>}{busy && !data && <p role="status">正在读取权威记录…</p>}
    {data && <><section className="ip-panel space-y-4"><div className="flex flex-wrap justify-between gap-3"><h2>所选动作的处置流程</h2><label className="text-sm">动作 <select aria-label="流程所选动作" value={selectedActionId || ""} onChange={e=>setSelectedActionId(e.target.value || undefined)}><option value="">请选择实际动作</option>{data.actions.map(a=><option key={a.actionId} value={a.actionId}>{a.actionId} · {a.state}</option>)}</select></label></div>{requestedAction && !data.actions.some(a=>a.actionId===requestedAction) && <p role="status" className="ip-amber">链接中的动作不在当前事件中，请核对动作 ID。</p>}<div className="ip-flow">{Object.entries({ "关联诊断":flow.diagnosis,"人工审批":flow.approval,"执行事实":flow.execution }).map(([label,text])=><div key={label}><strong>{label}</strong><span>{text}</span></div>)}</div><p className="ip-muted text-xs">仅描述所选动作及其诊断来源 · 诊断不代表根因确认，动作执行成功不代表事件已解决。</p></section><section className="space-y-3 rounded-xl border bg-white p-6"><h2 className="text-lg font-semibold">来源事实</h2><div className="flex flex-wrap gap-6"><span>状态 {data.incident.lifecycleState}</span><span>版本 {data.incident.version}</span><span>已关联告警 {data.incident.alertCount}</span></div><p className="text-xs text-slate-500">时间均为服务端 ISO 时间（含时区） · 最后告警 {data.incident.lastAlertReceivedAt}</p>
      {data.alerts.items.map(a=><div key={a.alertId} className="break-all rounded border p-3 text-sm"><p className="font-medium">{a.title}</p><p>{a.alertId} · {a.severity} · {a.sourceId} / {a.ruleId}</p><p>{a.service} / {a.environment} / {a.resourceType} / {a.resourceId}</p><p className="text-xs text-slate-500">来源报告 {a.occurredAt} · 接收 {a.receivedAt}</p></div>)}
      {data.alerts.items.length===0 && <p>暂无已处理的关联告警。</p>}{data.alerts.hasMore && <Button variant="outline" disabled={busy} onClick={()=>more("alerts")}>更多关联告警</Button>}</section>
      <DiagnosisPanel incident={data.incident} history={data.history} actions={data.actions} revision={revision} refresh={load} more={()=>more("history")} signal={signal} sourceId={source}/>
      <ActionPanel selectedActionId={selectedActionId} actions={data.actions} refresh={load} signal={signal} viewSource={d=>{setSource(d);document.getElementById("diagnosis")?.scrollIntoView({behavior:"smooth"});}}/>
      <section className="space-y-3 rounded-xl border bg-white p-6"><h2 className="text-lg font-semibold">Timeline · 只追加事实</h2>{data.timeline.items.map(t=><article key={t.eventId} className="break-all border-l-2 pl-4 text-sm"><p className="font-medium">#{t.sequenceNo} {t.eventType}</p><p className="text-xs text-slate-500">{t.occurredAt} · {t.actorType}/{t.actorId} · {t.relatedEntityType} {t.relatedEntityId}</p><pre className="whitespace-pre-wrap break-all text-xs">{JSON.stringify(t.summary,null,2)}</pre></article>)}{data.timeline.items.length===0 && <p>暂无时间线事实。</p>}{data.timeline.hasMore && <Button variant="outline" disabled={busy} onClick={()=>more("timeline")}>更多时间线</Button>}</section>
    </>}
  </div>;
}
