export type Severity = "SEV1" | "SEV2" | "SEV3" | "SEV4";

export type IncidentStatus =
  | "DETECTED"
  | "CORRELATED"
  | "INVESTIGATING"
  | "IDENTIFIED"
  | "MITIGATING"
  | "RESOLVED"
  | "CLOSED";

export type VerificationDecision =
  | "VERIFIED"
  | "REJECTED"
  | "NEEDS_MORE_EVIDENCE";

export type EvidenceType =
  | "METRIC_ANOMALY"
  | "LOG_ERROR_SPIKE"
  | "TRACE_LATENCY_OUTLIER"
  | "DEPLOYMENT_EVENT"
  | "CONFIG_CHANGE"
  | "DEPENDENCY_RELATION"
  | "PAST_POSTMORTEM"
  | "RUNBOOK";

export type UserRole =
  | "OWNER"
  | "ADMIN"
  | "SRE"
  | "SECURITY_ENGINEER"
  | "INCIDENT_COMMANDER"
  | "VIEWER"
  | "API_SERVICE";

export interface User {
  id: string;
  email: string;
  name: string;
  role: UserRole;
  tenantId: string;
}

export interface Tenant {
  id: string;
  name: string;
  slug: string;
}

export interface Incident {
  id: string;
  tenantId: string;
  title: string;
  summary: string;
  status: IncidentStatus;
  severity: Severity;
  primaryService: string;
  affectedServices: string[];
  correlationRule?: string;
  createdAt: string;
  updatedAt: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  acknowledgedBy?: string;
  resolvedBy?: string;
  leadInvestigatorId?: string;
  aiInvestigationStatus?: "IDLE" | "RUNNING" | "COMPLETED" | "FAILED";
}

export interface IncidentEvent {
  id: string;
  tenantId: string;
  incidentId: string;
  eventType: string;
  payload: Record<string, any>;
  actor: string;
  createdAt: string;
}

export interface Evidence {
  id: string;
  tenantId: string;
  incidentId: string;
  evidenceType: EvidenceType;
  source: string;
  summary: string;
  payload: Record<string, any>;
  confidenceScore: number;
  deepLink?: string;
  createdAt: string;
}

export interface RootCauseCandidate {
  id: string;
  tenantId: string;
  incidentId: string;
  serviceName: string;
  hypothesis: string;
  confidenceScore: number;
  reasoning: string;
  supportingEvidenceIds: string[];
  counterEvidenceIds: string[];
  recommendedActions: string[];
  verificationStatus: VerificationDecision | "PENDING";
  verifiedBy?: string;
  verifiedAt?: string;
  feedbackNotes?: string;
  createdAt: string;
}

export interface HumanVerificationRequest {
  candidateId: string;
  decision: VerificationDecision;
  feedbackNotes: string;
  verifiedBy?: string;
}

export interface Service {
  id: string;
  tenantId: string;
  name: string;
  tier: "TIER_1" | "TIER_2" | "TIER_3";
  ownerTeam: string;
  repositoryUrl: string;
  healthStatus: "HEALTHY" | "DEGRADED" | "CRITICAL";
  activeIncidentsCount: number;
}

export interface DependencyEdge {
  id: string;
  tenantId: string;
  fromService: string;
  toService: string;
  callType: string;
  p99LatencyMs?: number;
  errorRate?: number;
}

export interface TopologyNode {
  id: string;
  name: string;
  tier: string;
  health: "HEALTHY" | "DEGRADED" | "CRITICAL";
  activeIncidentsCount: number;
  isRootCause?: boolean;
  isInBlastRadius?: boolean;
}

export interface TopologyGraph {
  nodes: TopologyNode[];
  edges: Array<{
    source: string;
    target: string;
    type: string;
  }>;
  blastRadius: string[];
}

export interface DashboardSummary {
  activeIncidents: number;
  sev1Count: number;
  sev2Count: number;
  sev3Count: number;
  sev4Count: number;
  totalServices: number;
  mttaSeconds: number;
  mttrSeconds: number;
  recentIncidents: Incident[];
  detectionTrends: Array<{
    timestamp: string;
    count: number;
  }>;
}

export interface LogRecord {
  timestamp: string;
  service: string;
  level: "INFO" | "WARN" | "ERROR" | "DEBUG";
  message: string;
  traceId?: string;
  spanId?: string;
  attributes?: Record<string, any>;
}

export interface MetricSeries {
  service: string;
  metricName: string;
  unit: string;
  dataPoints: Array<{
    timestamp: string;
    value: number;
  }>;
}

export interface TraceSpan {
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  serviceName: string;
  operationName: string;
  startTimeUs: number;
  durationUs: number;
  statusCode: string;
  errorMessage?: string;
}

export interface TraceDetail {
  traceId: string;
  rootService: string;
  totalDurationMs: number;
  hasError: boolean;
  spans: TraceSpan[];
}

export interface CollectorStatus {
  collectorId: string;
  hostname: string;
  status: "UP" | "DEGRADED" | "DOWN";
  queueDepth: number;
  bufferHealth: number; // 0-100%
  droppedEvents: number;
  uptimeSeconds: number;
}

export interface IntegrationStatus {
  type: string;
  name: string;
  status: "CONNECTED" | "ERROR" | "CONFIGURING";
  endpoint: string;
  deadLetterCount: number;
  lastDeliveryStatus: string;
  lastDeliveryTime: string;
}

export interface AuditLog {
  id: string;
  tenantId: string;
  action: string;
  actor: string;
  resourceType: string;
  resourceId: string;
  details: string;
  timestamp: string;
  ipAddress?: string;
}

export interface Runbook {
  id: string;
  title: string;
  service: string;
  content: string;
  tags: string[];
  updatedAt: string;
}

export interface Deployment {
  id: string;
  service: string;
  version: string;
  author: string;
  deployedAt: string;
  commitHash: string;
  environment: string;
  status: "SUCCESS" | "FAILED" | "IN_PROGRESS";
}

export interface TenantQuota {
  tenantId: string;
  tier: string;
  incidentMonthlyQuota: number;
  incidentCurrentUsage: number;
  retentionDays: number;
}
