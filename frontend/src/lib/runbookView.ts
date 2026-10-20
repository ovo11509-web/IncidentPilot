import { isoTime, safeInteger } from "./incidentView.js";
import type { Evidence } from "../types/incident";
import type { RunbookCatalog, RunbookDocument } from "../types/runbook";

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw Error("手册数据格式错误");
  return value as Record<string, unknown>;
}
function text(value: unknown, max = 131072): string {
  if (typeof value !== "string" || !value.length || value.length > max)
    throw Error("手册文本格式错误");
  return value;
}
function digest(value: unknown) {
  const s = text(value, 64);
  if (!/^[a-f0-9]{64}$/.test(s)) throw Error("手册摘要格式错误");
  return s;
}
function array(value: unknown, max: number): unknown[] {
  if (!Array.isArray(value) || value.length > max) throw Error("手册列表格式错误");
  return value;
}
export function validateCatalog(value: unknown): RunbookCatalog {
  const data = object(value);
  const observedAt = isoTime(data.observedAt);
  const documents = array(data.documents, 100).map((v) => {
    const d = object(v);
    const runbookId = text(d.runbookId, 64);
    if (
      !/^[A-Z0-9-]+$/.test(runbookId) ||
      d.sourceType !== "INCIDENTPILOT_SYNTHETIC" ||
      !["PUBLISHED", "WITHDRAWN"].includes(String(d.status))
    )
      throw Error("手册身份或来源错误");
    const content = text(d.content),
      reviewedAt = isoTime(d.reviewedAt),
      validUntil = isoTime(d.validUntil);
    if (Date.parse(reviewedAt) >= Date.parse(validUntil)) throw Error("手册有效期错误");
    const sections = array(d.sections, 100).map((v) => {
      const s = object(v),
        lineStart = safeInteger(s.lineStart as number),
        lineEnd = safeInteger(s.lineEnd as number);
      const quote = text(s.quote),
        sectionId = text(s.sectionId, 128),
        lines = content.split("\n");
      if (
        lineStart < 1 ||
        lineEnd < lineStart ||
        lineEnd >= lines.length ||
        lines[lineStart - 1] !== `## ${sectionId}` ||
        lines.slice(lineStart - 1, lineEnd).join("\n") + "\n" !== quote
      )
        throw Error("手册章节与正文不符");
      return { sectionId, lineStart, lineEnd, quote, chunkDigest: digest(s.chunkDigest) };
    });
    if (!sections.length || new Set(sections.map((s) => s.sectionId)).size !== sections.length)
      throw Error("手册章节缺失或重复");
    function scope(key: string) {
      const values = array(d[key], 100).map((x) => text(x, 256));
      if (!values.length) throw Error("手册范围缺失");
      return values;
    }
    return {
      runbookId,
      version: text(d.version, 128),
      currentVersion: text(d.currentVersion, 128),
      title: text(d.title, 256),
      owner: text(d.owner, 256),
      author: text(d.author, 256),
      sourceType: "INCIDENTPILOT_SYNTHETIC" as const,
      status: d.status as RunbookDocument["status"],
      reviewedAt,
      validUntil,
      documentDigest: digest(d.documentDigest),
      services: scope("services"),
      environments: scope("environments"),
      resourceTypes: scope("resourceTypes"),
      content,
      sections
    };
  });
  if (
    !documents.length ||
    documents.reduce((sum, d) => sum + d.content.length, 0) > 1048576 ||
    new Set(documents.map((d) => `${d.runbookId}:${d.version}`)).size !== documents.length
  )
    throw Error("手册目录为空、超限或重复");
  return {
    observedAt,
    corpusVersion: text(data.corpusVersion, 128),
    corpusDigest: digest(data.corpusDigest),
    documents
  };
}
export function runbookLink(e: Pick<Evidence, "runbookId" | "runbookVersion" | "documentDigest">) {
  return `/admin/runbooks?${new URLSearchParams({ runbookId: e.runbookId, version: e.runbookVersion, digest: e.documentDigest })}`;
}
export function exactRunbook(catalog: RunbookCatalog, params: URLSearchParams) {
  if (!params.has("runbookId") && !params.has("version") && !params.has("digest"))
    return catalog.documents[0];
  return catalog.documents.find(
    (d) =>
      d.runbookId === params.get("runbookId") &&
      d.version === params.get("version") &&
      (!params.has("digest") || d.documentDigest === params.get("digest"))
  );
}
export function runbookStatus(d: RunbookDocument, observedAt: string) {
  if (d.status === "WITHDRAWN") return "已撤回 · 历史可读";
  if (d.version !== d.currentVersion) return "历史版本";
  if (Date.parse(observedAt) < Date.parse(d.reviewedAt)) return "尚未生效";
  if (Date.parse(observedAt) >= Date.parse(d.validUntil)) return "已过期 · 历史可读";
  return "当前有效版本 · 仍需匹配事件范围";
}
