// Dev server: ng serve proxies /api (REST + WS) to localhost:8000.
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  metricsStreamPath: '/api/v1/metrics/stream',
  apiKey: '',
};
