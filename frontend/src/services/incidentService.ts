import { incidentRequest } from "@/services/incidentApi";
import { validateWorkbench } from "@/lib/workbenchView";
import { id, isoTime, safeInteger } from "@/lib/incidentView";
import type {
  Action,
  AlertPage,
  Diagnosis,
  DiagnosisPage,
  Incident,
  IncidentCursor,
  IncidentFilters,
  IncidentPage,
  SelectIntent,
  TimelinePage
} from "@/types/incident";
const base = "/incident/v1";
function query(params: Record<string, string | number | undefined>) {
  const q = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) if (v !== undefined) q.set(k, String(v));
  return `?${q}`;
}
export const incidentService = {
  workbench: async (signal?: AbortSignal) =>
    validateWorkbench(await incidentRequest<unknown>(`${base}/workbench`, "0", signal)),
  list: (
    state?: string,
    cursor?: IncidentCursor | null,
    signal?: AbortSignal,
    filters: IncidentFilters = {}
  ) =>
    incidentRequest<IncidentPage>(
      `${base}/incidents${query({ limit: 20, state, ...filters, ...(cursor ? { afterId: id(cursor.afterId), afterEpisodeStartAt: isoTime(cursor.afterEpisodeStartAt) } : {}) })}`,
      "0",
      signal
    ),
  get: (i: string, signal?: AbortSignal) =>
    incidentRequest<Incident>(`${base}/incidents/${id(i)}`, "0", signal),
  alerts: (i: string, afterId?: string | null, signal?: AbortSignal) =>
    incidentRequest<AlertPage>(
      `${base}/incidents/${id(i)}/alerts${query({ limit: 20, afterId: afterId ? id(afterId) : undefined })}`,
      "0",
      signal
    ),
  timeline: (i: string, afterSequence = 0, signal?: AbortSignal) =>
    incidentRequest<TimelinePage>(
      `${base}/incidents/${id(i)}/timeline${query({ limit: 100, afterSequence: safeInteger(afterSequence) })}`,
      "0",
      signal
    ),
  diagnoses: (i: string, afterId?: string, signal?: AbortSignal) =>
    incidentRequest<DiagnosisPage>(
      `${base}/incidents/${id(i)}/diagnoses${query({ limit: 20, afterId: afterId ? id(afterId) : undefined })}`,
      "SUCCESS",
      signal
    ),
  diagnosis: (d: string, signal?: AbortSignal) =>
    incidentRequest<Diagnosis>(`${base}/diagnoses/${id(d)}`, "SUCCESS", signal),
  diagnose: (
    i: string,
    intent: { sourceRequestId: string; expectedIncidentVersion: number },
    signal?: AbortSignal
  ) => {
    safeInteger(intent.expectedIncidentVersion);
    return incidentRequest<Diagnosis>(
      `${base}/incidents/${id(i)}/diagnoses`,
      "SUCCESS",
      signal,
      intent
    );
  },
  select: (d: string, r: string, intent: SelectIntent, signal?: AbortSignal) => {
    safeInteger(intent.expectedIncidentVersion);
    return incidentRequest<{ actionId: string }>(
      `${base}/diagnoses/${id(d)}/recommendations/${encodeURIComponent(r)}/actions`,
      "SUCCESS",
      signal,
      intent
    );
  },
  actions: (i: string, signal?: AbortSignal) =>
    incidentRequest<Action[]>(`${base}/incidents/${id(i)}/actions`, "SUCCESS", signal),
  action: (a: string, signal?: AbortSignal) =>
    incidentRequest<Action>(`${base}/actions/${id(a)}`, "SUCCESS", signal),
  decision: (
    a: string,
    decision: "APPROVE" | "REJECT",
    expectedVersion: number,
    reason: string,
    signal?: AbortSignal
  ) =>
    incidentRequest<{ actionId: string }>(`${base}/actions/${id(a)}/decision`, "SUCCESS", signal, {
      decision,
      expectedVersion: safeInteger(expectedVersion),
      reason
    })
};
