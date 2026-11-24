import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from '../auth/auth.service';
import { authInterceptor } from '../auth/auth.interceptor';
import { CloudScaleApiService } from './cloudscale-api.service';

describe('CloudScaleApiService', () => {
  let api: CloudScaleApiService;
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    api = TestBed.inject(CloudScaleApiService);
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the in-memory bearer token to same-origin organization APIs', () => {
    auth.login({
      organizationSlug: 'northstar',
      email: 'operator@example.com',
      password: 'a-local-password',
    }).subscribe();
    http.expectOne('/api/v1/auth/login').flush({
      userId: 'user-1',
      organizationId: 'org-1',
      organizationSlug: 'northstar',
      organizationName: 'Northstar',
      role: 'ADMIN',
      accessToken: 'signed-access-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    api.listProjects('org-1').subscribe();

    const request = http.expectOne('/api/v1/organizations/org-1/projects');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer signed-access-token');
    request.flush([]);
  });

  it('never sends the bearer token outside same-origin API paths', () => {
    auth.login({
      organizationSlug: 'northstar',
      email: 'operator@example.com',
      password: 'a-local-password',
    }).subscribe();
    http.expectOne('/api/v1/auth/login').flush({
      userId: 'user-1',
      organizationId: 'org-1',
      organizationSlug: 'northstar',
      organizationName: 'Northstar',
      role: 'ADMIN',
      accessToken: 'signed-access-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    });

    const client = TestBed.inject(HttpClient);
    client.get('https://telemetry.example/collect').subscribe();
    const externalRequest = http.expectOne('https://telemetry.example/collect');
    expect(externalRequest.request.headers.has('Authorization')).toBeFalse();
    externalRequest.flush({});

    client.get('/assets/health.json').subscribe();
    const assetRequest = http.expectOne('/assets/health.json');
    expect(assetRequest.request.headers.has('Authorization')).toBeFalse();
    assetRequest.flush({});
  });
});
