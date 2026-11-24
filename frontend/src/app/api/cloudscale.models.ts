export interface Project {
  id: string;
  organizationId: string;
  key: string;
  name: string;
  description: string | null;
}

export interface Environment {
  id: string;
  projectId: string;
  key: string;
  name: string;
  description: string | null;
}

export interface TargetingRule {
  id?: string;
  priority: number;
  conditions: unknown;
  value: unknown;
}

export interface FeatureFlag {
  id: string;
  environmentId: string;
  key: string;
  name: string;
  description: string | null;
  enabled: boolean;
  publishedVersionId: string | null;
  revision: number;
}

export interface FeatureVersion {
  id: string;
  featureFlagId: string;
  versionNumber: number;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  value: unknown;
  targetingRules: TargetingRule[];
}

export interface EvaluationResult {
  featureKey: string;
  enabled: boolean;
  value: unknown;
  reason: string;
  publishedVersionId: string | null;
  versionNumber: number | null;
}

export interface RolloutStage {
  stageOrder: number;
  targetPercentage: number;
  durationMinutes: number;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
  startedAt: string | null;
  completedAt: string | null;
}

export interface Rollout {
  id: string;
  organizationId: string;
  featureFlagId: string;
  environmentId: string;
  status: 'DRAFT' | 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'ROLLED_BACK';
  currentPercentage: number;
  stages: RolloutStage[];
  createdAt: string;
  updatedAt: string;
}

export interface RolloutAssignment {
  rolloutId: string | null;
  bucket: number;
  percentage: number;
  assigned: boolean;
  status: Rollout['status'] | null;
}

export interface RolloutObservationResult {
  observationId: string;
  rolloutId: string;
  automaticallyPaused: boolean;
  rolloutStatus: Rollout['status'];
  recordedAt: string;
}

export interface AuditEvent {
  eventId: string;
  eventType: string;
  eventVersion: number;
  organizationId: string;
  entityId: string;
  entityType: string;
  source: string;
  correlationId: string | null;
  payload: unknown;
  occurredAt: string;
  recordedAt: string;
}

export interface TargetingRuleInput {
  priority: number;
  conditions: unknown;
  value: unknown;
}

export interface CreateFeatureRequest {
  key: string;
  name: string;
  description: string;
  enabled: boolean;
  value: unknown;
  targetingRules: TargetingRuleInput[];
}

export interface CreateRolloutRequest {
  featureFlagId: string;
  environmentId: string;
  stages: Array<{ targetPercentage: number; durationMinutes: number }>;
  healthPolicy: {
    metric: 'ERROR_RATE' | 'LATENCY_MS';
    threshold: number;
    minimumRequestCount: number;
    evaluationWindowSeconds: number;
    cooldownSeconds: number;
  } | null;
}
