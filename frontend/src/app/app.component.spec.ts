import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthSession } from './auth/auth.service';
import { AppComponent } from './app.component';

describe('AppComponent', () => {
  it('creates the application shell', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows sign-in before exposing protected management workspaces', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="auth-form"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="workspace-nav"]')).toBeNull();
  });

  it('disables sign-in until the required fields and password length are valid', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const submit = fixture.nativeElement.querySelector('.auth-submit') as HTMLButtonElement;

    expect(submit.disabled).toBeTrue();

    fixture.componentInstance.loginForm = {
      organizationSlug: 'northstar',
      email: 'not-an-email',
      password: 'short',
    };
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(submit.disabled).toBeTrue();

    fixture.componentInstance.loginForm = {
      organizationSlug: 'northstar',
      email: 'operator@example.com',
      password: 'a-local-password',
    };
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(submit.disabled).toBeFalse();
  });

  it('disables sign-in while authentication is pending and restores it after failure', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    const component = fixture.componentInstance;
    component.loginForm = {
      organizationSlug: 'northstar',
      email: 'operator@example.com',
      password: 'a-local-password',
    };
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const submit = fixture.nativeElement.querySelector('.auth-submit') as HTMLButtonElement;
    expect(submit.disabled).toBeFalse();

    component.signIn();
    fixture.detectChanges();
    expect(submit.disabled).toBeTrue();

    http.expectOne('/api/v1/auth/login').flush({
      code: 'INVALID_CREDENTIALS',
      message: 'Organization, email, or password is invalid',
    }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(submit.disabled).toBeFalse();
    http.verify();
  });

  it('keeps protected actions hidden and shows recovery copy after a failed sign-in', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    fixture.componentInstance.loginForm = {
      organizationSlug: 'northstar',
      email: 'operator@example.com',
      password: 'a-local-password',
    };

    fixture.componentInstance.signIn();
    http.expectOne('/api/v1/auth/login').flush({
      code: 'INVALID_CREDENTIALS',
      message: 'Organization, email, or password is invalid',
    }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
      .toContain('Organization, email, or password is invalid');
    expect(fixture.nativeElement.querySelector('[data-testid="workspace-nav"]')).toBeNull();
    http.verify();
  });

  it('opens the feature workspace and useful empty state after a successful sign-in', async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    fixture.componentInstance.loginForm = {
      organizationSlug: 'northstar',
      email: 'operator@example.com',
      password: 'a-local-password',
    };
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

    fixture.componentInstance.signIn();
    http.expectOne('/api/v1/auth/login').flush(session);
    const projectsRequest = http.expectOne('/api/v1/organizations/org-1/projects');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Loading projects');
    projectsRequest.flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="workspace-nav"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Create a project to begin');
    http.verify();
  });
});
