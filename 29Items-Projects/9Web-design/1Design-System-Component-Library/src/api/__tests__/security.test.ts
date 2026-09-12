import { getCorsOrigin } from '../security';

describe('security helpers', () => {
  const originalEnv = { ...process.env };

  afterEach(() => {
    process.env = { ...originalEnv };
  });

  it('allows local wildcard-style CORS by default', () => {
    delete process.env.CORS_ORIGIN;
    expect(getCorsOrigin()).toBe(true);
  });

  it('parses comma-separated CORS origins', () => {
    process.env.CORS_ORIGIN = 'https://one.example, https://two.example';
    expect(getCorsOrigin()).toEqual(['https://one.example', 'https://two.example']);
  });
});
