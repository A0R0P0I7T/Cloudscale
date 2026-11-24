import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthSession } from './auth/auth.service';
import { authInterceptor } from './auth/auth.interceptor';
import { FeatureFlag, FeatureVersion, Rollout } from './api/cloudscale.models';
import { AppComponent } from './app.component';

const session: AuthSession = {
  userId: 'user-1',
  organizationId: 'org-1',
  organizationSlug: 'northstar',
  organizationName: 'Northstar',
  role: 'ADMIN',
  accessToken: 'signed-token',
  tokenType: 'Bearer',
  expiresIn: 900,
};

const feature: FeatureFlag = {
  id: 'feature-1',
  environmentId: 'environment-1',
  key: 'checkout-v2',
  name: 'Checkout redesign',
  description: null,
  enabled: true,
  publishedVersionId: 'version-1',
  revision: 3,
};

describe('AppComponent API workflows', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<AppComponent>>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    http = TestBed.inject(HttpTestingController);

    const component = fixture.componentInstance;
    component.session = session;
    component.projects = [{
      id: 'project-1',
      organizationId: session.organizationId,
      key: 'web-store',
      name: 'Web store',
      description: null,
    }];
    component.environments = [{
      id: 'environment-1',
      projectId: 'project-1',
      key: 'production',
      name: 'Production',
      description: null,
    }];
    component.selectedProjectKey = 'web-store';
    component.selectedEnvironmentKey = 'production';
    component.features = [feature];
    component.selectedFeature = feature;
  });

  afterEach(() => http.verify());

  it('publishes a draft through the Gateway and refreshes the feature and version state', () => {
    const component = fixture.componentInstance;
    const draft: FeatureVersion = {
      id: 'version-2',
      featureFlagId: feature.id,
      versionNumber: 2,
      status: 'DRAFT',
      value: { variant: 'treatment' },
      targetingRules: [],
    };
    const published: FeatureVersion = { ...draft, status: 'PUBLISHED' };

    component.publish(draft);

    const publishRequest = http.expectOne(
      '/api/v1/organizations/org-1/projects/web-store/environments/production/features/checkout-v2/versions/2/publish',
    );
    expect(publishRequest.request.method).toBe('POST');
    publishRequest.flush(published);

    expect(component.successMessage).toBe('Version 2 published.');
    const updatedFeature = { ...feature, publishedVersionId: draft.id, revision: 4 };
    http.expectOne(
      '/api/v1/organizations/org-1/projects/web-store/environments/production/features',
    ).flush([updatedFeature]);
    http.expectOne(
      '/api/v1/organizations/org-1/projects/web-store/environments/production/features/checkout-v2/versions',
    ).flush([published]);

    expect(component.selectedFeature?.publishedVersionId).toBe('version-2');
    expect(component.versions).toEqual([published]);
    expect(component.errorMessage).toBe('');
  });

  it('evaluates the selected feature for the supplied user and attributes', () => {
    const component = fixture.componentInstance;
    component.evaluationUserId = 'customer-1042';
    component.evaluationAttributesJson = '{"country":"CA","plan":"pro"}';

    component.evaluate();

    const request = http.expectOne(
      '/api/v1/organizations/org-1/projects/web-store/environments/production/features/checkout-v2/evaluate',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      userId: 'customer-1042',
      attributes: { country: 'CA', plan: 'pro' },
    });
    request.flush({
      featureKey: 'checkout-v2',
      enabled: true,
      value: { variant: 'treatment' },
      reason: 'TARGETING_MATCH',
      publishedVersionId: 'version-1',
      versionNumber: 1,
    });

    expect(component.evaluationResult?.reason).toBe('TARGETING_MATCH');
    expect(component.evaluationResult?.value).toEqual({ variant: 'treatment' });
    expect(component.successMessage).toBe('Evaluation completed.');
    expect(component.errorMessage).toBe('');
  });

  it('shows read-only setup guidance instead of project creation controls to viewers', () => {
    const component = fixture.componentInstance;
    component.session = { ...session, role: 'VIEWER' };
    component.projects = [];
    component.environments = [];
    component.selectedProjectKey = '';
    component.selectedEnvironmentKey = '';
    component.projectFormOpen = true;
    component.environmentFormOpen = true;
    component.featureFormOpen = true;
    component.draftFormOpen = true;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty-state .button-primary')).toBeNull();
    expect(fixture.nativeElement.querySelector('.short-form')).toBeNull();
    expect(fixture.nativeElement.querySelector('.feature-create-form')).toBeNull();
    expect(fixture.nativeElement.querySelector('.draft-form')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Ask an administrator or developer');
  });

  it('rejects project, environment, feature, and version writes for a viewer before HTTP', () => {
    const component = fixture.componentInstance;
    component.session = { ...session, role: 'VIEWER' };
    component.newProject = { key: 'another-project', name: 'Another project', description: '' };
    component.createProject();
    expect(component.errorMessage).toContain('read-only');
    http.expectNone('/api/v1/organizations/org-1/projects');

    component.errorMessage = '';
    component.newEnvironment = { key: 'staging', name: 'Staging', description: '' };
    component.createEnvironment();
    expect(component.errorMessage).toContain('read-only');
    http.expectNone('/api/v1/organizations/org-1/projects/web-store/environments');

    component.errorMessage = '';
    component.newFeature.valueJson = '{}';
    component.createFeature();
    expect(component.errorMessage).toContain('read-only');
    http.expectNone(
      '/api/v1/organizations/org-1/projects/web-store/environments/production/features',
    );

    component.errorMessage = '';
    component.draftValueJson = '{}';
    component.createDraft();
    expect(component.errorMessage).toContain('read-only');
    http.expectNone(
      '/api/v1/organizations/org-1/projects/web-store/environments/production/features/checkout-v2/versions',
    );
  });

  it('hides and blocks health observation submission for a viewer', () => {
    const component = fixture.componentInstance;
    const rollout: Rollout = {
      id: 'rollout-1',
      organizationId: session.organizationId,
      featureFlagId: feature.id,
      environmentId: 'environment-1',
      status: 'IN_PROGRESS',
      currentPercentage: 25,
      stages: [],
      createdAt: '2026-11-24T00:00:00Z',
      updatedAt: '2026-11-24T00:00:00Z',
    };
    component.session = { ...session, role: 'VIEWER' };
    component.workspace = 'rollouts';
    component.rollouts = [rollout];
    component.selectedRolloutId = rollout.id;
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.observation-form')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Only administrators and developers can report health');

    component.submitObservation();

    expect(component.errorMessage).toContain('read-only');
    http.expectNone('/api/v1/organizations/org-1/rollouts/rollout-1/observations');
  });
});
