import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const origin = globalThis.location?.origin;
  if (!origin) {
    return next(request);
  }
  let url: URL;
  try {
    url = new URL(request.url, origin);
  } catch {
    return next(request);
  }
  if (url.origin !== origin
    || !(url.pathname === '/api/v1' || url.pathname.startsWith('/api/v1/'))
    || url.pathname.startsWith('/api/v1/auth/')) {
    return next(request);
  }

  const token = inject(AuthService).accessToken();
  if (!token) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
