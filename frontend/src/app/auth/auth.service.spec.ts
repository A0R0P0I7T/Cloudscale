import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService, AuthSession } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends credentials and exposes the bearer token only after a successful login', () => {
    const credentials = {
      organizationSlug: 'northstar',
      email: 'operator@example.com',
      password: 'a-local-password',
    };
    const expected: AuthSession = {
      userId: 'user-1',
      organizationId: 'org-1',
      organizationSlug: 'northstar',
      organizationName: 'Northstar',
      role: 'ADMIN',
      accessToken: 'signed-access-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    };
    let response: AuthSession | undefined;

    service.login(credentials).subscribe((value) => response = value);

    const request = http.expectOne('/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(credentials);
    expect(service.accessToken()).toBeNull();

    request.flush(expected);

    expect(response).toEqual(expected);
    expect(service.currentSession()).toEqual(expected);
    expect(service.accessToken()).toBe(expected.accessToken);
  });

  it('clears the in-memory session on sign out', () => {
    service.signOut();

    expect(service.currentSession()).toBeNull();
    expect(service.accessToken()).toBeNull();
  });
});
