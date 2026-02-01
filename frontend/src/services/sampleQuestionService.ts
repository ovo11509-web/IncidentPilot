import { api } from "@/services/api";

export interface SampleQuestion {
  id: string;
  title?: string | null;
  description?: string | null;
  question: string;
}

export async function listSampleQuestions() {
  return api.get<SampleQuestion[]>("/rag/sample-questions");
}
