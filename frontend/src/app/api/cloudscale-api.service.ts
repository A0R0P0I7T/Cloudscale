import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AuditEvent,
  CreateFeatureRequest,
  CreateRolloutRequest,
  Environment,
  EvaluationResult,
  FeatureFlag,
  FeatureVersion,
  Project,
  Rollout,
  RolloutAssignment,
  RolloutObservationResult,
  TargetingRuleInput,
} from './cloudscale.models';

@Injectable({ providedIn: 'root' })
export class CloudScaleApiService {
  constructor(private readonly http: HttpClient) {}

  listProjects(organizationId: string): Observable<Project[]> {
    return this.http.get<Project[]>(`${this.organizationPath(organizationId)}/projects`);
  }

  createProject(organizationId: string, request: { key: string; name: string; description: string }): Observable<Project> {
    return this.http.post<Project>(`${this.organizationPath(organizationId)}/projects`, request);
  }

  listEnvironments(organizationId: string, projectKey: string): Observable<Environment[]> {
    return this.http.get<Environment[]>(`${this.projectPath(organizationId, projectKey)}/environments`);
  }

  createEnvironment(
    organizationId: string,
    projectKey: string,
    request: { key: string; name: string; description: string },
  ): Observable<Environment> {
    return this.http.post<Environment>(
      `${this.projectPath(organizationId, projectKey)}/environments`,
      request,
    );
  }

  listFeatures(organizationId: string, projectKey: string, environmentKey: string): Observable<FeatureFlag[]> {
    return this.http.get<FeatureFlag[]>(`${this.environmentPath(organizationId, projectKey, environmentKey)}/features`);
  }

  createFeature(
    organizationId: string,
    projectKey: string,
    environmentKey: string,
    request: CreateFeatureRequest,
  ): Observable<FeatureFlag> {
    return this.http.post<FeatureFlag>(
      `${this.environmentPath(organizationId, projectKey, environmentKey)}/features`,
      request,
    );
  }

  updateFeature(
    organizationId: string,
    projectKey: string,
    environmentKey: string,
    featureKey: string,
    request: { name?: string; description?: string; enabled?: boolean },
  ): Observable<FeatureFlag> {
    return this.http.patch<FeatureFlag>(
      `${this.featurePath(organizationId, projectKey, environmentKey, featureKey)}`,
      request,
    );
  }

  listVersions(
    organizationId: string,
    projectKey: string,
    environmentKey: string,
    featureKey: string,
  ): Observable<FeatureVersion[]> {
    return this.http.get<FeatureVersion[]>(
      `${this.featurePath(organizationId, projectKey, environmentKey, featureKey)}/versions`,
    );
  }

  createVersion(
    organizationId: string,
    projectKey: string,
    environmentKey: string,
    featureKey: string,
    request: { value: unknown; targetingRules: TargetingRuleInput[] },
  ): Observable<FeatureVersion> {
    return this.http.post<FeatureVersion>(
      `${this.featurePath(organizationId, projectKey, environmentKey, featureKey)}/versions`,
      request,
    );
  }

  publishVersion(
    organizationId: string,
    projectKey: string,
    environmentKey: string,
    featureKey: string,
    versionNumber: number,
  ): Observable<FeatureVersion> {
    return this.http.post<FeatureVersion>(
      `${this.featurePath(organizationId, projectKey, environmentKey, featureKey)}/versions/${versionNumber}/publish`,
      {},
    );
  }

  evaluate(
    organizationId: string,
    projectKey: string,
    environmentKey: string,
    featureKey: string,
    request: { userId: string; attributes: Record<string, unknown> },
  ): Observable<EvaluationResult> {
    return this.http.post<EvaluationResult>(
      `${this.featurePath(organizationId, projectKey, environmentKey, featureKey)}/evaluate`,
      request,
    );
  }

  listRollouts(organizationId: string): Observable<Rollout[]> {
    return this.http.get<Rollout[]>(`${this.organizationPath(organizationId)}/rollouts`);
  }

  createRollout(organizationId: string, request: CreateRolloutRequest): Observable<Rollout> {
    return this.http.post<Rollout>(`${this.organizationPath(organizationId)}/rollouts`, request);
  }

  rolloutAction(
    organizationId: string,
    rolloutId: string,
    action: 'start' | 'pause' | 'resume' | 'advance' | 'rollback',
  ): Observable<Rollout> {
    return this.http.post<Rollout>(
      `${this.organizationPath(organizationId)}/rollouts/${encodeURIComponent(rolloutId)}/${action}`,
      {},
    );
  }

  observeRollout(
    organizationId: string,
    rolloutId: string,
    request: { requestCount: number; errorRate: number; latencyMs: number; observedAt: string },
  ): Observable<RolloutObservationResult> {
    return this.http.post<RolloutObservationResult>(
      `${this.organizationPath(organizationId)}/rollouts/${encodeURIComponent(rolloutId)}/observations`,
      request,
    );
  }

  checkAssignment(
    organizationId: string,
    request: { featureFlagId: string; environmentId: string; userId: string },
  ): Observable<RolloutAssignment> {
    return this.http.post<RolloutAssignment>(
      `${this.organizationPath(organizationId)}/rollouts/assignment`,
      request,
    );
  }

  listAuditEvents(organizationId: string, page = 0, size = 50): Observable<AuditEvent[]> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<AuditEvent[]>(`${this.organizationPath(organizationId)}/audit-events`, { params });
  }

  private organizationPath(organizationId: string): string {
    return `/api/v1/organizations/${encodeURIComponent(organizationId)}`;
  }

  private projectPath(organizationId: string, projectKey: string): string {
    return `${this.organizationPath(organizationId)}/projects/${encodeURIComponent(projectKey)}`;
  }

  private environmentPath(organizationId: string, projectKey: string, environmentKey: string): string {
    return `${this.projectPath(organizationId, projectKey)}/environments/${encodeURIComponent(environmentKey)}`;
  }

  private featurePath(
    organizationId: string,
    projectKey: string,
    environmentKey: string,
    featureKey: string,
  ): string {
    return `${this.environmentPath(organizationId, projectKey, environmentKey)}/features/${encodeURIComponent(featureKey)}`;
  }
}
