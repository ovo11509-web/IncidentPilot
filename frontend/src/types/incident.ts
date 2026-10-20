export type Incident = {
  incidentId: string;
  lifecycleState: string;
  version: number;
  alertCount: number;
  episodeStartAt: string;
  firstAlertReceivedAt: string;
  lastAlertReceivedAt: string;
  summary?: { title: string; service: string; environment: string; severity: string } | null;
};
export type IncidentFilters = {
  q?: string;
  service?: string;
  environment?: string;
  severity?: string;
};
export type IncidentCursor = { afterEpisodeStartAt: string; afterId: string };
export type IncidentPage = {
  items: Incident[];
  hasMore: boolean;
  nextCursor: IncidentCursor | null;
};
export type Alert = {
  alertId: string;
  sourceId: string;
  ruleId: string;
  title: string;
  severity: string;
  service: string;
  environment: string;
  resourceType: string;
  resourceId: string;
  occurredAt: string;
  receivedAt: string;
};
export type AlertPage = { items: Alert[]; hasMore: boolean; nextAfterId: string | null };
export type TimelineFact = {
  eventId: string;
  sequenceNo: number;
  eventType: string;
  actorType: string;
  actorId: string;
  occurredAt: string;
  correlationId: string;
  relatedEntityType?: string;
  relatedEntityId?: string;
  summary: Record<string, unknown>;
};
export type TimelinePage = { items: TimelineFact[]; hasMore: boolean; nextAfterSequence: number };
export type Recommendation = {
  recommendationId: string;
  recommendationDigest: string;
  convertible: boolean;
  [key: string]: unknown;
};
export type Evidence = {
  citationId: string;
  runbookId: string;
  runbookVersion: string;
  sectionId: string;
  lineStart: number;
  lineEnd: number;
  documentDigest: string;
  chunkDigest: string;
  quote: string;
};
export type Diagnosis = {
  diagnosisId: string;
  incidentId: string;
  requestState: string;
  outcome?: string;
  resultDigest?: string;
  stale: boolean;
  evidenceApplicability: string;
  deadlineExceeded: boolean;
  nextStep: string;
  deadlineAt: string;
  snapshot?: Record<string, unknown>;
  result?: {
    recommendations?: Recommendation[];
    claims?: { claimId: string; statement: string; uncertainty: string; citationIds: string[] }[];
    missingFacts?: string[];
    limitations?: string[];
    evidence?: Evidence[];
    documentSnapshots?: Record<string, unknown>[];
    [key: string]: unknown;
  };
};
export type DiagnosisPage = { items: Diagnosis[]; hasMore: boolean; nextAfterId: string };
export type Receipt = {
  receiptId: string;
  resultStatus: string;
  resultCode: string;
  disposition: string;
  occurredAt?: string;
  acceptedAt?: string;
  summaryAvailable: boolean;
  summary?: { desiredState: string; applied: boolean | null; proofCode?: string };
};
export type Action = {
  actionId: string;
  incidentId: string;
  state: string;
  version: number;
  toolName: string;
  toolVersion: string;
  targetType: string;
  targetId: string;
  bindingDigest: string;
  parametersAvailable: boolean;
  parameters?: { desiredState: string };
  originalActionId?: string;
  lastReasonCode?: string;
  nextStep: string;
  diagnosisSource?: {
    diagnosisId: string;
    recommendationId: string;
    diagnosisDigest: string;
    recommendationDigest: string;
    factsDigest: string;
  };
  approval: {
    status: string;
    approvalDueAt?: string;
    authorizationDueAt?: string;
    decidedAt?: string;
    deciderId?: string;
    reasonCode?: string;
  };
  execution?: {
    executionId: string;
    executionKey: string;
    ticketStatus?: string;
    resultStatus?: string;
    resultCode?: string;
  };
  receipts?: Receipt[];
};
export type SelectIntent = {
  sourceRequestId: string;
  expectedIncidentVersion: number;
  diagnosisDigest: string;
  recommendationDigest: string;
  originalActionId?: string;
};
export type Workbench = {
  observedAt: string;
  windowStart: string;
  windowEnd: string;
  counts: {
    activeIncidents: number;
    pendingApprovalActions: number;
    executingActions: number;
    unknownActions: number;
  };
  alertBuckets: { startAt: string; endAt: string; count: number }[];
  attentionIncidents: {
    incidentId: string;
    lifecycleState: string;
    version: number;
    alertCount: number;
    lastAlertReceivedAt: string;
    updatedAt: string;
    pendingApprovalActionCount: number;
    executingActionCount: number;
    unknownActionCount: number;
  }[];
  pendingActions: {
    actionId: string;
    incidentId: string;
    state: "PENDING_APPROVAL";
    createdAt: string;
    approvalDueAt: string;
  }[];
  recentActivity: {
    eventId: string;
    incidentId: string;
    sequenceNo: number;
    eventType: string;
    createdAt: string;
  }[];
};
