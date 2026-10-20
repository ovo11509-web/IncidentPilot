import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { exactRunbook, runbookLink, runbookStatus } from "@/lib/runbookView";
import { getRunbooks } from "@/services/runbookService";
import type { RunbookCatalog } from "@/types/runbook";

export function RunbookCenterPage() {
  const [params] = useSearchParams(),
    [catalog, setCatalog] = useState<RunbookCatalog>(),
    [query, setQuery] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [revision, setRevision] = useState(0);
  useEffect(() => {
    const c = new AbortController();
    setBusy(true);
    setError("");
    setCatalog(undefined);
    getRunbooks(c.signal)
      .then((data) => {
        if (!c.signal.aborted) setCatalog(data);
      })
      .catch((e) => {
        if (!c.signal.aborted) setError((e as Error).message);
      })
      .finally(() => {
        if (!c.signal.aborted) setBusy(false);
      });
    return () => c.abort();
  }, [revision]);
  const selected = catalog && exactRunbook(catalog, params);
  const rows =
    catalog?.documents.filter((d) =>
      [d.title, d.runbookId, d.version, ...d.services, ...d.environments, ...d.resourceTypes]
        .join(" ")
        .toLocaleLowerCase()
        .includes(query.trim().toLocaleLowerCase())
    ) || [];
  return (
    <div className="incidentpilot-page min-w-0 space-y-6 break-words">
      <header className="flex flex-wrap justify-between gap-3">
        <div>
          <p className="ip-eyebrow">RESPONSE KNOWLEDGE</p>
          <h1 className="mt-2 text-2xl font-semibold">Runbook 处置手册中心</h1>
          <p className="ip-muted mt-2 text-sm">从来源症状到只读检查，让诊断建议有据可查。</p>
        </div>
        <Button variant="outline" disabled={busy} onClick={() => setRevision((v) => v + 1)}>
          {busy ? "读取中…" : "刷新目录"}
        </Button>
      </header>
      <div className="ip-panel space-y-2">
        <p className="ip-amber text-sm">
          IncidentPilot 自建本地模拟资料 · 非生产操作手册 · 此页只读，不执行处置。
        </p>
        <p className="ip-muted text-xs">
          当前目录不能替代历史诊断中的冻结引用原文。版本有效仍须通过事件范围与服务端资格检查。
        </p>
        {catalog && (
          <p className="ip-muted break-all text-xs">
            语料 {catalog.corpusVersion} · {catalog.documents.length} 份版本 · 读取时间{" "}
            {catalog.observedAt}
          </p>
        )}
      </div>
      {busy && <p role="status">正在读取已校验的手册语料…</p>}
      {error && (
        <p role="alert" className="ip-panel ip-amber">
          {error}
        </p>
      )}
      {catalog && (
        <div className="grid min-w-0 gap-6 xl:grid-cols-[280px_minmax(0,1fr)]">
          <section className="ip-panel min-w-0 space-y-4">
            <label className="block space-y-2 text-sm">
              <span>搜索手册与适用范围</span>
              <Input
                className="border-slate-600 bg-slate-900 text-slate-100 placeholder:text-slate-400"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="标题、服务、环境、资源类型"
              />
            </label>
            <nav aria-label="处置手册目录" className="space-y-3">
              {rows.map((d) => (
                <Link
                  key={`${d.runbookId}:${d.version}`}
                  aria-current={
                    selected?.runbookId === d.runbookId && selected.version === d.version
                      ? "page"
                      : undefined
                  }
                  className={`block rounded-lg border p-4 text-sm ${selected === d ? "border-cyan-400 bg-cyan-400/10" : ""}`}
                  to={runbookLink({
                    runbookId: d.runbookId,
                    runbookVersion: d.version,
                    documentDigest: d.documentDigest
                  })}
                >
                  <strong className="block">{d.title}</strong>
                  <span className="ip-muted mt-1 block break-all text-xs">
                    {d.runbookId} · v{d.version}
                  </span>
                  <span className="ip-muted mt-2 block text-xs">
                    {runbookStatus(d, catalog.observedAt)}
                  </span>
                </Link>
              ))}
            </nav>
            {!rows.length && (
              <p role="status" className="ip-muted text-sm">
                没有匹配手册，试试其他关键词。
              </p>
            )}
          </section>
          {!selected ? (
            <section className="ip-panel space-y-3">
              <h2 className="font-semibold">引用版本不可匹配</h2>
              <p role="alert" className="ip-amber text-sm">
                指定手册版本不存在，或文档摘要与历史引用不同。没有自动回退到其他版本。
              </p>
              <p className="ip-muted text-sm">
                请返回原事件诊断，复核保存的历史引用原文；也可以从目录明确选择当前手册。
              </p>
              <Link to="/admin/incidents">返回事件中心 →</Link>
            </section>
          ) : (
            <article className="ip-panel min-w-0 space-y-5">
              <header>
                <h2 className="text-xl font-semibold">{selected.title}</h2>
                <p className="ip-muted mt-2 break-all text-sm">
                  {selected.runbookId} / v{selected.version} ·{" "}
                  {runbookStatus(selected, catalog.observedAt)}
                </p>
              </header>
              <dl className="grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="ip-muted">适用服务</dt>
                  <dd className="break-all">{selected.services.join("、")}</dd>
                </div>
                <div>
                  <dt className="ip-muted">环境 / 资源类型</dt>
                  <dd className="break-all">
                    {selected.environments.join("、")} / {selected.resourceTypes.join("、")}
                  </dd>
                </div>
                <div>
                  <dt className="ip-muted">作者 / 维护人</dt>
                  <dd>
                    {selected.author} / {selected.owner}
                  </dd>
                </div>
                <div>
                  <dt className="ip-muted">审阅时间 / 有效期截至</dt>
                  <dd className="break-all text-xs">
                    {selected.reviewedAt}
                    <br />
                    {selected.validUntil}
                  </dd>
                </div>
              </dl>
              <details>
                <summary className="cursor-pointer text-sm">文档完整性摘要</summary>
                <p className="ip-muted mt-2 break-all font-mono text-xs">
                  SHA-256 {selected.documentDigest}
                </p>
                <p className="ip-muted mt-2 break-all text-xs">语料摘要 {catalog.corpusDigest}</p>
              </details>
              <section className="space-y-3">
                <h3 className="font-semibold">可引用章节</h3>
                {selected.sections.map((s) => (
                  <details className="rounded-lg border p-3" key={s.sectionId}>
                    <summary className="cursor-pointer text-sm">
                      {s.sectionId} · 行 {s.lineStart}–{s.lineEnd}
                    </summary>
                    <pre className="mt-3 whitespace-pre-wrap break-all text-sm">{s.quote}</pre>
                    <p className="ip-muted mt-3 break-all font-mono text-xs">
                      章节 SHA-256 {s.chunkDigest}
                    </p>
                  </details>
                ))}
              </section>
              <section className="space-y-3">
                <h3 className="font-semibold">完整手册原文</h3>
                <pre className="rounded-lg border p-4 text-sm leading-7 whitespace-pre-wrap break-all">
                  {selected.content}
                </pre>
              </section>
            </article>
          )}
        </div>
      )}
    </div>
  );
}
