import { incidentRequest } from "@/services/incidentApi";
import { validateCatalog } from "@/lib/runbookView";
export async function getRunbooks(signal?: AbortSignal) {
  return validateCatalog(await incidentRequest<unknown>("/incident/v1/runbooks", "0", signal));
}
