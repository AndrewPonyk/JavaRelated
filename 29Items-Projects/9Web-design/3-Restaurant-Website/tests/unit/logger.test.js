import { afterEach, describe, expect, it, vi } from 'vitest';
import { logger } from '../../src/backend/logger.js';

describe('logger', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('writes structured info and error logs', () => {
    const stdout = vi.spyOn(process.stdout, 'write').mockImplementation(() => true);
    const stderr = vi.spyOn(process.stderr, 'write').mockImplementation(() => true);

    logger.info('startup', { port: 8080 });
    logger.error('failure', { code: 'test_error' });

    expect(JSON.parse(stdout.mock.calls[0][0])).toMatchObject({ level: 'info', message: 'startup', port: 8080 });
    expect(JSON.parse(stderr.mock.calls[0][0])).toMatchObject({
      level: 'error',
      message: 'failure',
      code: 'test_error'
    });
  });
});
