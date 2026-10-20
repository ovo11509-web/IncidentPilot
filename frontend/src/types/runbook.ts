export type RunbookDocument = {
  runbookId: string;
  version: string;
  currentVersion: string;
  title: string;
  owner: string;
  author: string;
  sourceType: "INCIDENTPILOT_SYNTHETIC";
  status: "PUBLISHED" | "WITHDRAWN";
  reviewedAt: string;
  validUntil: string;
  documentDigest: string;
  services: string[];
  environments: string[];
  resourceTypes: string[];
  content: string;
  sections: {
    sectionId: string;
    lineStart: number;
    lineEnd: number;
    quote: string;
    chunkDigest: string;
  }[];
};
export type RunbookCatalog = {
  observedAt: string;
  corpusVersion: string;
  corpusDigest: string;
  documents: RunbookDocument[];
};
