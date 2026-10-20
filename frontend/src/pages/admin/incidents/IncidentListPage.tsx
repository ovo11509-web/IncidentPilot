import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { incidentService } from "@/services/incidentService";
import {
  appendUnique,
  incidentStates,
  incidentSeverities,
  normalizeIncidentFilters
} from "@/lib/incidentView";
import { stateLabel } from "@/lib/workbenchView";
import type { IncidentFilters, IncidentPage } from "@/types/incident";

const empty = { state: "", q: "", service: "", environment: "", severity: "" };
export function IncidentListPage() {
  const [draft, setDraft] = useState(empty);
  const [applied, setApplied] = useState<{ state: string; filters: IncidentFilters }>({
    state: "",
    filters: {}
  });
  const [page, setPage] = useState<IncidentPage>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const request = useRef<AbortController>();
  useEffect(() => {
    request.current?.abort();
    const c = new AbortController();
    request.current = c;
    setPage(undefined);
    setError("");
    setBusy(true);
    incidentService
      .list(applied.state || undefined, null, c.signal, applied.filters)
      .then((value) => {
        if (!c.signal.aborted) setPage(value);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError(String(e.message));
      })
      .finally(() => {
        if (!c.signal.aborted) setBusy(false);
      });
    return () => c.abort();
  }, [applied, refresh]);
  const more = async () => {
    if (!page || busy || !page.hasMore || !page.nextCursor) return;
    const c = new AbortController();
    request.current?.abort();
    request.current = c;
    setBusy(true);
    setError("");
    try {
      const next = await incidentService.list(
        applied.state || undefined,
        page.nextCursor,
        c.signal,
        applied.filters
      );
      if (!c.signal.aborted)
        setPage({ ...next, items: appendUnique(page.items, next.items, (x) => x.incidentId) });
    } catch (e) {
      if (!c.signal.aborted) setError(String((e as Error).message));
    } finally {
      if (!c.signal.aborted) setBusy(false);
    }
  };
  useEffect(() => () => request.current?.abort(), []);
  function apply(e: FormEvent) {
    e.preventDefault();
    try {
      const filters = normalizeIncidentFilters(draft);
      request.current?.abort();
      setApplied({ state: draft.state, filters });
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <div className="incidentpilot-page min-w-0 space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">事件中心</h1>
          <p className="ip-muted mt-2 text-sm">事件事实、证据与人工审批 · 模拟处置</p>
        </div>
        <Button variant="outline" onClick={() => setRefresh((x) => x + 1)}>
          刷新列表
        </Button>
      </div>
      <form className="ip-panel grid gap-4 sm:grid-cols-2 xl:grid-cols-3" onSubmit={apply}>
        <label className="space-y-2 text-sm">
          <span className="block">关键词</span>
          <Input
            value={draft.q}
            maxLength={128}
            placeholder="事件ID或关联告警标题 · 字面包含"
            onChange={(e) => setDraft({ ...draft, q: e.target.value })}
          />
        </label>
        <label className="space-y-2 text-sm">
          <span className="block">服务</span>
          <Input
            value={draft.service}
            maxLength={128}
            placeholder="忽略大小写精确匹配"
            onChange={(e) => setDraft({ ...draft, service: e.target.value })}
          />
        </label>
        <label className="space-y-2 text-sm">
          <span className="block">环境</span>
          <Input
            value={draft.environment}
            maxLength={128}
            placeholder="忽略大小写精确匹配"
            onChange={(e) => setDraft({ ...draft, environment: e.target.value })}
          />
        </label>
        <label className="space-y-2 text-sm">
          <span className="block">生命周期</span>
          <select
            className="w-full rounded border bg-slate-900 p-2"
            value={draft.state}
            onChange={(e) => setDraft({ ...draft, state: e.target.value })}
          >
            <option value="">全部状态</option>
            {incidentStates.map((x) => (
              <option key={x} value={x}>
                {stateLabel(x)}
              </option>
            ))}
          </select>
        </label>
        <label className="space-y-2 text-sm">
          <span className="block">严重级别</span>
          <select
            className="w-full rounded border bg-slate-900 p-2"
            value={draft.severity}
            onChange={(e) => setDraft({ ...draft, severity: e.target.value })}
          >
            <option value="">全部级别</option>
            {incidentSeverities.map((x) => (
              <option key={x}>{x}</option>
            ))}
          </select>
        </label>
        <div className="flex flex-wrap items-end gap-2">
          <Button type="submit">应用筛选</Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              request.current?.abort();
              setDraft(empty);
              setApplied({ state: "", filters: {} });
            }}
          >
            清空筛选
          </Button>
        </div>
        <p className="ip-muted text-xs sm:col-span-2 xl:col-span-3">
          应用后在服务端筛选全部事件；组合条件由同一条关联告警满足。下表展示首条告警摘要，可能与命中筛选的后续告警不同。
        </p>
      </form>
      {error && (
        <p role="alert" className="ip-panel ip-amber">
          {error}
        </p>
      )}
      {busy && !page && <p role="status">正在读取事件…</p>}
      {page && (
        <div className="overflow-x-auto rounded-xl border">
          <table className="w-full text-left text-sm">
            <thead>
              <tr>
                {[
                  "事件 / 首条告警摘要",
                  "服务 / 环境",
                  "严重级别",
                  "状态",
                  "告警数 / 版本",
                  "开始时间",
                  "最后告警"
                ].map((x) => (
                  <th key={x} className="p-4 whitespace-nowrap">
                    {x}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {page.items.map((i) => (
                <tr key={i.incidentId} className="border-t">
                  <td className="min-w-64 p-4">
                    <Link
                      className="break-all font-medium text-cyan-300"
                      to={`/admin/incidents/${i.incidentId}`}
                    >
                      {i.incidentId}
                    </Link>
                    <p className="mt-2 break-words">{i.summary?.title || "摘要不可用"}</p>
                  </td>
                  <td className="p-4 break-all">
                    {i.summary ? (
                      <>
                        {i.summary.service}
                        <br />
                        {i.summary.environment}
                      </>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="p-4">{i.summary?.severity || "—"}</td>
                  <td className="p-4 whitespace-nowrap">{stateLabel(i.lifecycleState)}</td>
                  <td className="p-4 whitespace-nowrap">
                    {i.alertCount} / v{i.version}
                  </td>
                  <td className="p-4 whitespace-nowrap">{i.episodeStartAt}</td>
                  <td className="p-4 whitespace-nowrap">{i.lastAlertReceivedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {page.items.length === 0 && <p className="ip-muted p-6">没有符合条件的事件。</p>}
        </div>
      )}
      {page?.hasMore && (
        <Button variant="outline" onClick={more} disabled={busy}>
          {busy ? "读取中…" : "更多事件"}
        </Button>
      )}
    </div>
  );
}
