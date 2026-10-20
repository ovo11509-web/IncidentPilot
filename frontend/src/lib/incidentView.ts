import type { Action, Diagnosis, Recommendation, IncidentFilters } from "../types/incident";
function safeRequestId(value: unknown): string | undefined {
  return typeof value === "string" &&
    /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value)
    ? value
    : undefined;
}

export class IncidentError extends Error {
  constructor(
    public status: number,
    public code: string,
    public requestId?: string,
    public write = false
  ) {
    code = /^(?:0|[A-Z][A-Z0-9_]{0,63})$/.test(code) ? code : "INVALID_ENVELOPE";
    requestId = safeRequestId(requestId);
    const uncertain = ["REQUEST_TIMEOUT", "TRANSPORT_UNKNOWN", "INVALID_ENVELOPE"].includes(code);
    const reason =
      code === "REQUEST_TIMEOUT"
        ? "请求超时"
        : code === "TRANSPORT_UNKNOWN"
          ? "网络传输失败"
          : code === "INVALID_ENVELOPE"
            ? "响应格式无效"
            : status === 401
              ? "登录失效"
              : status === 403
                ? "权限不足"
                : status === 404
                  ? "记录不存在"
                  : status === 409
                    ? "状态或绑定冲突，请刷新并重新审阅"
                    : "请求失败";
    super(
      `${reason}${uncertain ? (write ? "；写入结果未知，请先核对历史，不会自动重发" : "；读取未完成，可手动刷新") : ""} · ${code}${status ? ` · HTTP ${status}` : ""}${requestId ? ` · ${requestId}` : ""}`
    );
    this.code = code;
    this.requestId = requestId;
  }
}
export function unwrap<T>(
  payload: { code?: unknown; data?: T; requestId?: string },
  status: number,
  expected: "0" | "SUCCESS"
): T {
  if (
    !payload ||
    typeof payload !== "object" ||
    Array.isArray(payload) ||
    typeof payload.code !== "string"
  )
    throw new IncidentError(status, "INVALID_ENVELOPE");
  if (status < 200 || status >= 300 || payload?.code !== expected)
    throw new IncidentError(
      status,
      typeof payload?.code === "string" ? payload.code : "INVALID_ENVELOPE",
      payload?.requestId
    );
  if (payload.data === undefined || payload.data === null)
    throw new IncidentError(status, "INVALID_ENVELOPE", payload.requestId);
  return payload.data as T;
}
/** Classify transport failures without preserving response text, URL or credentials. No retries. */
export async function requestIncident<T>(
  send: (signal: AbortSignal) => Promise<Response>,
  expected: "0" | "SUCCESS",
  signal?: AbortSignal,
  write = false,
  timeoutMs = 60000
): Promise<T> {
  signal?.throwIfAborted();
  const controller = new AbortController();
  let timedOut = false;
  const abort = () => controller.abort();
  signal?.addEventListener("abort", abort, { once: true });
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  try {
    const response = await send(controller.signal);
    const requestId = safeRequestId(response.headers?.get("X-Request-Id"));
    let payload;
    try {
      payload = await response.json();
    } catch (e) {
      signal?.throwIfAborted();
      if (timedOut) throw new IncidentError(response.status, "REQUEST_TIMEOUT", requestId, write);
      throw new IncidentError(
        response.status,
        e instanceof SyntaxError ? "INVALID_ENVELOPE" : "TRANSPORT_UNKNOWN",
        requestId,
        write
      );
    }
    signal?.throwIfAborted();
    if (payload && typeof payload === "object" && !Array.isArray(payload))
      payload = { ...payload, requestId: safeRequestId(payload.requestId) || requestId };
    return unwrap<T>(payload, response.status, expected);
  } catch (e) {
    signal?.throwIfAborted();
    if (e instanceof IncidentError) throw new IncidentError(e.status, e.code, e.requestId, write);
    throw new IncidentError(
      0,
      timedOut ? "REQUEST_TIMEOUT" : "TRANSPORT_UNKNOWN",
      undefined,
      write
    );
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener("abort", abort);
  }
}
export function id(value: unknown): string {
  if (typeof value !== "string" || !/^[0-9]{1,20}$/.test(value)) throw new Error("无效字符串 ID");
  return value;
}
export function safeInteger(value: number): number {
  if (!Number.isSafeInteger(value) || value < 0)
    throw new Error("版本或序列超出安全整数范围，操作已禁用");
  return value;
}
export function isoTime(value: unknown): string {
  if (
    typeof value !== "string" ||
    !/^\d{4}-\d\d-\d\dT.*(?:Z|[+-]\d\d:\d\d)$/.test(value) ||
    !Number.isFinite(Date.parse(value))
  )
    throw new Error("无效带时区时间");
  return value;
}
export function rememberIntent<T>(intents: Map<string, T>, key: string, create: () => T): T {
  const previous = intents.get(key);
  if (previous) return previous;
  const next = create();
  intents.set(key, next);
  return next;
}
export function appendUnique<T>(before: T[], after: T[], key: (item: T) => string | number): T[] {
  return Array.from(new Map([...before, ...after].map((item) => [key(item), item])).values());
}
export const incidentStates = ["OPEN", "ACKNOWLEDGED", "MITIGATING", "RESOLVED", "CLOSED"];
export const incidentSeverities = ["CRITICAL", "HIGH", "MEDIUM", "LOW"];
export function normalizeIncidentFilters(filters: IncidentFilters): IncidentFilters {
  const result: IncidentFilters = {};
  for (const key of ["q", "service", "environment"] as const) {
    const raw = filters[key] || "";
    if (/[\p{Cc}]/u.test(raw)) throw Error("筛选条件不能包含控制字符");
    const value = raw.trim().normalize("NFC");
    if (value.length > 128) throw Error("筛选条件最多128字符");
    if (value) result[key] = value;
  }
  const severity = (filters.severity || "").trim().toUpperCase();
  if (severity && !incidentSeverities.includes(severity)) throw Error("无效严重级别");
  if (severity) result.severity = severity;
  return result;
}
export function canSelect(
  d: Pick<
    Diagnosis,
    "requestState" | "outcome" | "stale" | "evidenceApplicability" | "deadlineExceeded"
  >,
  r: Pick<Recommendation, "convertible">,
  state: string
): boolean {
  return (
    incidentStates.includes(state) &&
    state !== "CLOSED" &&
    d.requestState === "COMPLETED" &&
    d.outcome === "SUPPORTED" &&
    !d.stale &&
    !d.deadlineExceeded &&
    d.evidenceApplicability === "VALID" &&
    r.convertible === true
  );
}
export function canApprove(a: Action): boolean {
  return (
    a.state === "PENDING_APPROVAL" &&
    a.approval.status === "PENDING" &&
    Number.isSafeInteger(a.version) &&
    a.version >= 0 &&
    a.parametersAvailable === true &&
    ["READY", "PAUSED"].includes(a.parameters?.desiredState || "") &&
    a.toolName === "simulation.set-state" &&
    a.toolVersion === "1" &&
    a.targetType === "SIM_SERVICE" &&
    Boolean(a.targetId) &&
    /^[a-f0-9]{64}$/.test(a.bindingDigest)
  );
}
export function shouldPoll(
  d: Pick<Diagnosis, "requestState" | "deadlineExceeded">[],
  a: Pick<Action, "state">[]
): boolean {
  return (
    d.some((x) => x.requestState === "RUNNING" && !x.deadlineExceeded) ||
    a.some((x) => ["APPROVED", "DISPATCHING", "EXECUTING"].includes(x.state))
  );
}
export async function singleRequest<T>(send: () => Promise<T>): Promise<T> {
  return send();
}

export function serialReads() {
  let tail = Promise.resolve();
  return (read: () => Promise<void>): Promise<void> => {
    const next = tail.then(read);
    tail = next.catch(() => {});
    return next;
  };
}
