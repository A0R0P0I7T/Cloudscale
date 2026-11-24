import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface AuthSession {
  userId: string;
  organizationId: string;
  organizationSlug: string;
  organizationName: string;
  role: 'ADMIN' | 'DEVELOPER' | 'VIEWER';
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export interface LoginRequest {
  organizationSlug: string;
  email: string;
  password: string;
}

export interface RegisterRequest {
  organizationName: string;
  organizationSlug: string;
  fullName: string;
  email: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly sessionState = signal<AuthSession | null>(null);

  constructor(private readonly http: HttpClient) {}

  currentSession(): AuthSession | null {
    return this.sessionState();
  }

  accessToken(): string | null {
    return this.sessionState()?.accessToken ?? null;
  }

  login(request: LoginRequest): Observable<AuthSession> {
    return this.http.post<AuthSession>('/api/v1/auth/login', request).pipe(
      tap((session) => this.sessionState.set(session)),
    );
  }

  register(request: RegisterRequest): Observable<AuthSession> {
    return this.http.post<AuthSession>('/api/v1/auth/register', request).pipe(
      tap((session) => this.sessionState.set(session)),
    );
  }

  signOut(): void {
    this.sessionState.set(null);
  }
}
