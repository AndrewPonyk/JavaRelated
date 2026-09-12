// Production defaults: same-origin API behind nginx / CloudFront.
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  // ws(s)://<host>/api/v1/metrics/stream — built from window.location at runtime.
  metricsStreamPath: '/api/v1/metrics/stream',
  // Set when the deployment uses AUTH_MODE=api_key (JWT flows use localStorage).
  apiKey: '',
};
