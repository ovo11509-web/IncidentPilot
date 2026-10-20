import { storage } from "@/utils/storage";
import { requestIncident } from "@/lib/incidentView";

// Spec A reads retain code=0; B/C retain SUCCESS. No global interceptor changes.
export async function incidentRequest<T>(
  path: string,
  expected: "0" | "SUCCESS",
  signal?: AbortSignal,
  body?: unknown
): Promise<T> {
  return requestIncident<T>(
    async (requestSignal) => {
      const token = storage.getToken();
      const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || "/api/ragent"}${path}`, {
        method: body === undefined ? "GET" : "POST",
        signal: requestSignal,
        headers: {
          ...(token ? { Authorization: token } : {}),
          ...(body === undefined ? {} : { "Content-Type": "application/json" })
        },
        ...(body === undefined ? {} : { body: JSON.stringify(body) })
      });
      if (response.status === 401) {
        storage.clearAuth();
        window.location.assign("/login");
      }
      return response;
    },
    expected,
    signal,
    body !== undefined
  );
}
