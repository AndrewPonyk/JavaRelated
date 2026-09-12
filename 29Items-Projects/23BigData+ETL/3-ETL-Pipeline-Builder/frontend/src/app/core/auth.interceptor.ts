import { HttpInterceptorFn } from '@angular/common/http';

import { environment } from '../../environments/environment';

/**
 * Attaches credentials when the deployment requires them (AUTH_MODE on the API):
 * a JWT from localStorage ('etl_token', placed there by the IdP redirect flow)
 * wins over the build-time API key. No-ops when neither is present (local dev).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = localStorage.getItem('etl_token');
  if (token) {
    return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
  }
  if (environment.apiKey) {
    return next(req.clone({ setHeaders: { 'X-API-Key': environment.apiKey } }));
  }
  return next(req);
};
