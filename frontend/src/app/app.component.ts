import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { DatePipe, JsonPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize, Observable } from 'rxjs';
import { AuthService, AuthSession } from './auth/auth.service';
import { CloudScaleApiService } from './api/cloudscale-api.service';
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
} from './api/cloudscale.models';

type Workspace = 'features' | 'evaluation' | 'rollouts' | 'audit';
type AuthMode = 'login' | 'register';

@Component({
  selector: 'cs-root',
  standalone: true,
  imports: [FormsModule, DatePipe, JsonPipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
  changeDetection: ChangeDetectionStrategy.Eager,
})
export class AppComponent implements OnInit {
  session: AuthSession | null;
  authMode: AuthMode = 'login';
  workspace: Workspace = 'features';
  loginForm = { organizationSlug: '', email: '', password: '' };
  registerForm = {
    organizationName: '',
    organizationSlug: '',
    fullName: '',
    email: '',
    password: '',
  };

  projects: Project[] = [];
  environments: Environment[] = [];
  features: FeatureFlag[] = [];
  versions: FeatureVersion[] = [];
  rollouts: Rollout[] = [];
  auditEvents: AuditEvent[] = [];
  selectedProjectKey = '';
  selectedEnvironmentKey = '';
  selectedFeature: FeatureFlag | null = null;
  selectedRolloutId = '';

  newProject = { key: '', name: '', description: '' };
  newEnvironment = { key: '', name: '', description: '' };
  newFeature = {
    key: '',
    name: '',
    description: '',
    enabled: false,
    valueJson: '',
    targetingRulesJson: '[]',
  };
  draftValueJson = '';
  draftRulesJson = '[]';
  evaluationUserId = '';
  evaluationAttributesJson = '{}';
  evaluationResult: EvaluationResult | null = null;
  newRollout = {
    stagesText: '',
    healthEnabled: false,
    metric: 'ERROR_RATE' as 'ERROR_RATE' | 'LATENCY_MS',
    threshold: null as number | null,
    minimumRequestCount: null as number | null,
    evaluationWindowSeconds: null as number | null,
    cooldownSeconds: null as number | null,
  };
  observationForm = {
    requestCount: null as number | null,
    errorRate: null as number | null,
    latencyMs: null as number | null,
  };
  assignmentUserId = '';
  rolloutAssignment: RolloutAssignment | null = null;
  observationResult: RolloutObservationResult | null = null;

  projectFormOpen = false;
  environmentFormOpen = false;
  featureFormOpen = false;
  draftFormOpen = false;
  rolloutFormOpen = false;
  busyAction = '';
  featuresLoading = false;
  rolloutsLoading = false;
  auditLoading = false;
  authError = '';
  errorMessage = '';
  successMessage = '';

  constructor(
    private readonly authService: AuthService,
    private readonly api: CloudScaleApiService,
  ) {
    this.session = this.authService.currentSession();
  }

  ngOnInit(): void {
    if (this.session) {
      this.loadProjects();
    }
  }

  get canWrite(): boolean {
    return this.session !== null && this.session.role !== 'VIEWER';
  }

  get selectedProject(): Project | null {
    return this.projects.find((project) => project.key === this.selectedProjectKey) ?? null;
  }

  get selectedEnvironment(): Environment | null {
    return this.environments.find((environment) => environment.key === this.selectedEnvironmentKey) ?? null;
  }

  get selectedRollout(): Rollout | null {
    return this.rollouts.find((rollout) => rollout.id === this.selectedRolloutId) ?? null;
  }

  signIn(): void {
    this.authError = '';
    const action = 'login';
    this.busyAction = action;
    this.authService.login(this.loginForm).pipe(finalize(() => {
      if (this.busyAction === action) {
        this.busyAction = '';
      }
    })).subscribe({
      next: (session) => this.onAuthenticated(session),
      error: (error: unknown) => this.authError = this.describeError(error),
    });
  }

  register(): void {
    this.authError = '';
    const action = 'register';
    this.busyAction = action;
    this.authService.register(this.registerForm).pipe(finalize(() => {
      if (this.busyAction === action) {
        this.busyAction = '';
      }
    })).subscribe({
      next: (session) => this.onAuthenticated(session),
      error: (error: unknown) => this.authError = this.describeError(error),
    });
  }

  signOut(): void {
    this.authService.signOut();
    this.session = null;
    this.projects = [];
    this.environments = [];
    this.features = [];
    this.versions = [];
    this.rollouts = [];
    this.auditEvents = [];
    this.selectedFeature = null;
    this.selectedProjectKey = '';
    this.selectedEnvironmentKey = '';
    this.workspace = 'features';
    this.projectFormOpen = false;
    this.environmentFormOpen = false;
    this.featureFormOpen = false;
    this.draftFormOpen = false;
    this.rolloutFormOpen = false;
    this.clearMessages();
  }

  setWorkspace(workspace: Workspace): void {
    this.workspace = workspace;
    this.clearMessages();
    if (workspace === 'rollouts') {
      this.loadRollouts();
    } else if (workspace === 'audit') {
      this.loadAuditEvents();
    }
  }

  changeProject(): void {
    this.environments = [];
    this.features = [];
    this.versions = [];
    this.selectedEnvironmentKey = '';
    this.selectedFeature = null;
    this.clearMessages();
    if (this.selectedProjectKey) {
      this.loadEnvironments();
    }
  }

  changeEnvironment(): void {
    this.features = [];
    this.versions = [];
    this.selectedFeature = null;
    this.clearMessages();
    if (this.selectedEnvironmentKey) {
      this.loadFeatures();
    }
  }

  selectFeature(feature: FeatureFlag): void {
    this.selectedFeature = feature;
    this.versions = [];
    this.evaluationResult = null;
    this.clearMessages();
    this.loadVersions();
  }

  createProject(): void {
    if (!this.session) {
      this.errorMessage = 'Sign in before creating a project.';
      return;
    }
    if (this.rejectReadOnlyWrite()) {
      return;
    }
    if (!this.newProject.key.trim() || !this.newProject.name.trim()) {
      this.errorMessage = 'Enter a project key and name.';
      return;
    }
    this.run('project', this.api.createProject(this.session.organizationId, this.newProject), (project) => {
      this.newProject = { key: '', name: '', description: '' };
      this.projectFormOpen = false;
      this.successMessage = `Project ${project.name} created.`;
      this.loadProjects(project.key);
    });
  }

  createEnvironment(): void {
    if (!this.session) {
      this.errorMessage = 'Sign in before creating an environment.';
      return;
    }
    if (this.rejectReadOnlyWrite()) {
      return;
    }
    if (!this.selectedProject || !this.newEnvironment.key.trim() || !this.newEnvironment.name.trim()) {
      this.errorMessage = 'Choose a project, then enter an environment key and name.';
      return;
    }
    this.run('environment', this.api.createEnvironment(
      this.session.organizationId,
      this.selectedProject.key,
      this.newEnvironment,
    ), (environment) => {
      this.newEnvironment = { key: '', name: '', description: '' };
      this.environmentFormOpen = false;
      this.successMessage = `Environment ${environment.name} created.`;
      this.selectedEnvironmentKey = environment.key;
      this.loadEnvironments(environment.key);
    });
  }

  createFeature(): void {
    if (!this.session) {
      this.errorMessage = 'Sign in before creating a feature.';
      return;
    }
    if (this.rejectReadOnlyWrite()) {
      return;
    }
    if (!this.selectedProject || !this.selectedEnvironment) {
      this.errorMessage = 'Choose a project and environment first.';
      return;
    }
    const value = this.parseJson(this.newFeature.valueJson, 'Default value');
    const rules = this.parseArray<TargetingRuleInput>(this.newFeature.targetingRulesJson, 'Targeting rules');
    if (!value.valid) {
      this.errorMessage = value.error;
      return;
    }
    if (!rules.valid) {
      this.errorMessage = rules.error;
      return;
    }
    if (!this.newFeature.key.trim() || !this.newFeature.name.trim()) {
      this.errorMessage = 'Enter a feature key and name.';
      return;
    }
    const request: CreateFeatureRequest = {
      key: this.newFeature.key.trim(),
      name: this.newFeature.name.trim(),
      description: this.newFeature.description.trim(),
      enabled: this.newFeature.enabled,
      value: value.value,
      targetingRules: rules.value,
    };
    this.run('feature', this.api.createFeature(
      this.session.organizationId,
      this.selectedProject.key,
      this.selectedEnvironment.key,
      request,
    ), (feature) => {
      this.newFeature = { key: '', name: '', description: '', enabled: false, valueJson: '', targetingRulesJson: '[]' };
      this.featureFormOpen = false;
      this.selectedFeature = feature;
      this.successMessage = `Feature ${feature.key} created with a draft version.`;
      this.loadFeatures(feature.key);
    });
  }

  toggleFeature(feature: FeatureFlag): void {
    if (!this.session || !this.selectedProject || !this.selectedEnvironment || !this.canWrite) {
      return;
    }
    this.run('toggle-feature', this.api.updateFeature(
      this.session.organizationId,
      this.selectedProject.key,
      this.selectedEnvironment.key,
      feature.key,
      { enabled: !feature.enabled },
    ), (updated) => {
      this.replaceFeature(updated);
      this.successMessage = `${updated.key} is now ${updated.enabled ? 'enabled' : 'disabled'}.`;
    });
  }

  createDraft(): void {
    if (!this.session) {
      this.errorMessage = 'Sign in before creating a draft.';
      return;
    }
    if (this.rejectReadOnlyWrite()) {
      return;
    }
    if (!this.selectedProject || !this.selectedEnvironment || !this.selectedFeature) {
      this.errorMessage = 'Select a feature before creating a draft.';
      return;
    }
    const value = this.parseJson(this.draftValueJson, 'Version value');
    const rules = this.parseArray<TargetingRuleInput>(this.draftRulesJson, 'Targeting rules');
    if (!value.valid) {
      this.errorMessage = value.error;
      return;
    }
    if (!rules.valid) {
      this.errorMessage = rules.error;
      return;
    }
    this.run('draft', this.api.createVersion(
      this.session.organizationId,
      this.selectedProject.key,
      this.selectedEnvironment.key,
      this.selectedFeature.key,
      { value: value.value, targetingRules: rules.value },
    ), (version) => {
      this.draftValueJson = '';
      this.draftRulesJson = '[]';
      this.draftFormOpen = false;
      this.successMessage = `Draft version ${version.versionNumber} created.`;
      this.loadVersions();
    });
  }

  publish(version: FeatureVersion): void {
    if (!this.session || !this.selectedProject || !this.selectedEnvironment || !this.selectedFeature || !this.canWrite) {
      return;
    }
    this.run('publish', this.api.publishVersion(
      this.session.organizationId,
      this.selectedProject.key,
      this.selectedEnvironment.key,
      this.selectedFeature.key,
      version.versionNumber,
    ), () => {
      this.successMessage = `Version ${version.versionNumber} published.`;
      this.loadFeatures(this.selectedFeature?.key);
    });
  }

  evaluate(): void {
    if (!this.session || !this.selectedProject || !this.selectedEnvironment || !this.selectedFeature) {
      return;
    }
    if (!this.evaluationUserId.trim()) {
      this.errorMessage = 'Enter the stable user ID to evaluate.';
      return;
    }
    const attributes = this.parseObject(this.evaluationAttributesJson, 'Attributes');
    if (!attributes.valid) {
      this.errorMessage = attributes.error;
      return;
    }
    this.run('evaluate', this.api.evaluate(
      this.session.organizationId,
      this.selectedProject.key,
      this.selectedEnvironment.key,
      this.selectedFeature.key,
      { userId: this.evaluationUserId.trim(), attributes: attributes.value },
    ), (result) => {
      this.evaluationResult = result;
      this.successMessage = 'Evaluation completed.';
    });
  }

  createRollout(): void {
    if (!this.session || !this.selectedFeature || !this.selectedEnvironment || !this.canWrite) {
      this.errorMessage = 'Choose a feature and environment before creating a rollout.';
      return;
    }
    const stages = this.parseStages(this.newRollout.stagesText);
    if (!stages.valid) {
      this.errorMessage = stages.error;
      return;
    }
    if (this.newRollout.healthEnabled && (this.newRollout.threshold === null
      || this.newRollout.minimumRequestCount === null
      || this.newRollout.evaluationWindowSeconds === null
      || this.newRollout.cooldownSeconds === null)) {
      this.errorMessage = 'Complete every health-policy field or turn the policy off.';
      return;
    }
    const request: CreateRolloutRequest = {
      featureFlagId: this.selectedFeature.id,
      environmentId: this.selectedEnvironment.id,
      stages: stages.value,
      healthPolicy: this.newRollout.healthEnabled ? {
        metric: this.newRollout.metric,
        threshold: Number(this.newRollout.threshold),
        minimumRequestCount: Number(this.newRollout.minimumRequestCount),
        evaluationWindowSeconds: Number(this.newRollout.evaluationWindowSeconds),
        cooldownSeconds: Number(this.newRollout.cooldownSeconds),
      } : null,
    };
    this.run('create-rollout', this.api.createRollout(this.session.organizationId, request), (rollout) => {
      this.rolloutFormOpen = false;
      this.selectedRolloutId = rollout.id;
      this.successMessage = `Rollout ${rollout.id} created as a draft.`;
      this.loadRollouts();
    });
  }

  applyRolloutAction(rollout: Rollout, action: 'start' | 'pause' | 'resume' | 'advance' | 'rollback'): void {
    if (!this.session || !this.canWrite) {
      return;
    }
    this.run(`rollout-${action}`, this.api.rolloutAction(this.session.organizationId, rollout.id, action), (updated) => {
      this.replaceRollout(updated);
      this.successMessage = `Rollout ${action} completed.`;
    });
  }

  submitObservation(): void {
    if (!this.session) {
      this.errorMessage = 'Sign in before reporting rollout health.';
      return;
    }
    if (this.rejectReadOnlyWrite()) {
      return;
    }
    if (!this.selectedRolloutId) {
      this.errorMessage = 'Choose an active rollout before reporting health.';
      return;
    }
    if (this.observationForm.requestCount === null || this.observationForm.errorRate === null
      || this.observationForm.latencyMs === null) {
      this.errorMessage = 'Enter request count, error rate, and latency from a real observation.';
      return;
    }
    this.run('observation', this.api.observeRollout(this.session.organizationId, this.selectedRolloutId, {
      requestCount: this.observationForm.requestCount,
      errorRate: this.observationForm.errorRate,
      latencyMs: this.observationForm.latencyMs,
      observedAt: new Date().toISOString(),
    }), (result) => {
      this.observationResult = result;
      this.successMessage = result.automaticallyPaused
        ? 'Health policy paused the rollout.'
        : 'Health observation recorded.';
      this.loadRollouts();
    });
  }

  checkAssignment(): void {
    if (!this.session || !this.selectedFeature || !this.selectedEnvironment || !this.assignmentUserId.trim()) {
      this.errorMessage = 'Choose a feature and enter a stable user ID.';
      return;
    }
    this.run('assignment', this.api.checkAssignment(this.session.organizationId, {
      featureFlagId: this.selectedFeature.id,
      environmentId: this.selectedEnvironment.id,
      userId: this.assignmentUserId.trim(),
    }), (assignment) => {
      this.rolloutAssignment = assignment;
      this.successMessage = 'Deterministic assignment calculated.';
    });
  }

  refreshAudit(): void {
    this.loadAuditEvents();
  }

  private onAuthenticated(session: AuthSession): void {
    this.session = session;
    this.workspace = 'features';
    this.authError = '';
    this.successMessage = `Signed in to ${session.organizationName}.`;
    this.loadProjects();
  }

  private loadProjects(selectKey?: string): void {
    if (!this.session) {
      return;
    }
    this.run('load-projects', this.api.listProjects(this.session.organizationId), (projects) => {
      this.projects = projects;
      if (selectKey && projects.some((project) => project.key === selectKey)) {
        this.selectedProjectKey = selectKey;
      } else if (!projects.some((project) => project.key === this.selectedProjectKey)) {
        this.selectedProjectKey = projects[0]?.key ?? '';
      }
      if (this.selectedProjectKey) {
        this.loadEnvironments();
      } else {
        this.environments = [];
        this.features = [];
        this.selectedEnvironmentKey = '';
        this.selectedFeature = null;
      }
    }, true);
  }

  private loadEnvironments(selectKey?: string): void {
    if (!this.session || !this.selectedProject) {
      return;
    }
    this.run('load-environments', this.api.listEnvironments(
      this.session.organizationId, this.selectedProject.key,
    ), (environments) => {
      this.environments = environments;
      if (selectKey && environments.some((environment) => environment.key === selectKey)) {
        this.selectedEnvironmentKey = selectKey;
      } else if (!environments.some((environment) => environment.key === this.selectedEnvironmentKey)) {
        this.selectedEnvironmentKey = environments[0]?.key ?? '';
      }
      if (this.selectedEnvironmentKey) {
        this.loadFeatures();
      } else {
        this.features = [];
        this.selectedFeature = null;
      }
    }, true);
  }

  private loadFeatures(selectKey?: string): void {
    if (!this.session || !this.selectedProject || !this.selectedEnvironment) {
      this.features = [];
      return;
    }
    this.featuresLoading = true;
    this.run('load-features', this.api.listFeatures(
      this.session.organizationId, this.selectedProject.key, this.selectedEnvironment.key,
    ), (features) => {
      this.features = features;
      if (selectKey) {
        this.selectedFeature = features.find((feature) => feature.key === selectKey) ?? null;
      } else if (this.selectedFeature) {
        this.selectedFeature = features.find((feature) => feature.key === this.selectedFeature?.key) ?? null;
      } else {
        this.selectedFeature = features[0] ?? null;
      }
      if (this.selectedFeature) {
        this.loadVersions();
      }
    }, true, () => this.featuresLoading = false);
  }

  private loadVersions(featureKey?: string): void {
    if (!this.session || !this.selectedProject || !this.selectedEnvironment || !this.selectedFeature) {
      this.versions = [];
      return;
    }
    const key = featureKey ?? this.selectedFeature.key;
    this.run('load-versions', this.api.listVersions(
      this.session.organizationId, this.selectedProject.key, this.selectedEnvironment.key, key,
    ), (versions) => this.versions = versions, true);
  }

  private loadRollouts(): void {
    if (!this.session) {
      return;
    }
    this.rolloutsLoading = true;
    this.run('load-rollouts', this.api.listRollouts(this.session.organizationId), (rollouts) => {
      this.rollouts = rollouts;
      if (!rollouts.some((rollout) => rollout.id === this.selectedRolloutId)) {
        this.selectedRolloutId = rollouts[0]?.id ?? '';
      }
    }, true, () => this.rolloutsLoading = false);
  }

  private loadAuditEvents(): void {
    if (!this.session) {
      return;
    }
    this.auditLoading = true;
    this.run('load-audit', this.api.listAuditEvents(this.session.organizationId), (events) => {
      this.auditEvents = events;
    }, true, () => this.auditLoading = false);
  }

  private replaceFeature(updated: FeatureFlag): void {
    this.features = this.features.map((feature) => feature.id === updated.id ? updated : feature);
    if (this.selectedFeature?.id === updated.id) {
      this.selectedFeature = updated;
    }
  }

  private replaceRollout(updated: Rollout): void {
    this.rollouts = this.rollouts.map((rollout) => rollout.id === updated.id ? updated : rollout);
  }

  private parseJson(text: string, label: string): { valid: true; value: unknown } | { valid: false; error: string } {
    if (!text.trim()) {
      return { valid: false, error: `${label} is required.` };
    }
    try {
      return { valid: true, value: JSON.parse(text) as unknown };
    } catch {
      return { valid: false, error: `${label} must be valid JSON.` };
    }
  }

  private parseObject(text: string, label: string): { valid: true; value: Record<string, unknown> } | { valid: false; error: string } {
    const parsed = this.parseJson(text, label);
    if (!parsed.valid) {
      return parsed;
    }
    if (!parsed.value || Array.isArray(parsed.value) || typeof parsed.value !== 'object') {
      return { valid: false, error: `${label} must be a JSON object.` };
    }
    return { valid: true, value: parsed.value as Record<string, unknown> };
  }

  private parseArray<T>(text: string, label: string): { valid: true; value: T[] } | { valid: false; error: string } {
    const parsed = this.parseJson(text, label);
    if (!parsed.valid) {
      return parsed;
    }
    if (!Array.isArray(parsed.value)) {
      return { valid: false, error: `${label} must be a JSON array.` };
    }
    return { valid: true, value: parsed.value as T[] };
  }

  private parseStages(text: string): {
    valid: true;
    value: Array<{ targetPercentage: number; durationMinutes: number }>;
  } | { valid: false; error: string } {
    const stages = text.split(',').map((part) => {
      const [target, duration] = part.split(':').map((item) => Number(item.trim()));
      return { targetPercentage: target, durationMinutes: duration };
    });
    if (stages.length === 0 || stages.some((stage) => !Number.isFinite(stage.targetPercentage)
      || !Number.isFinite(stage.durationMinutes) || stage.targetPercentage < 0
      || stage.targetPercentage > 100 || stage.durationMinutes < 1)) {
      return { valid: false, error: 'Stages use target:minutes pairs, for example 10:5,100:10.' };
    }
    return { valid: true, value: stages };
  }

  private run<T>(
    key: string,
    request: Observable<T>,
    onSuccess: (value: T) => void,
    quiet = false,
    onFinally?: () => void,
  ): void {
    this.busyAction = key;
    this.errorMessage = '';
    if (!quiet) {
      this.successMessage = '';
    }
    request.pipe(finalize(() => {
      if (this.busyAction === key) {
        this.busyAction = '';
      }
      onFinally?.();
    })).subscribe({
      next: onSuccess,
      error: (error: unknown) => this.errorMessage = this.describeError(error),
    });
  }

  private clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }

  private rejectReadOnlyWrite(): boolean {
    if (this.canWrite) {
      return false;
    }
    this.errorMessage = 'Your role is read-only. Ask an administrator or developer to make this change.';
    return true;
  }

  private describeError(error: unknown): string {
    if (error && typeof error === 'object' && 'error' in error) {
      const body = (error as { error?: { message?: string } }).error;
      if (body?.message) {
        return body.message;
      }
    }
    return 'The request could not be completed. Check your connection and try again.';
  }
}
