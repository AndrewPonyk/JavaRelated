// @vitest-environment node

import { randomUUID } from 'node:crypto';

import { describe, expect, it } from 'vitest';

import { AuthenticationError, SessionTokenService } from './auth';

describe('SessionTokenService', () => {
  const service = new SessionTokenService('test-secret-that-is-longer-than-thirty-two-characters');

  it('round-trips a signed user session', async () => {
    const user = { id: randomUUID(), displayName: 'Editor' };
    await expect(service.verify(`Bearer ${await service.create(user)}`)).resolves.toEqual(user);
  });

  it('rejects missing, corrupted, and wrongly signed bearer tokens', async () => {
    await expect(service.verify(undefined)).rejects.toBeInstanceOf(AuthenticationError);
    await expect(service.verify('Bearer malformed')).rejects.toBeInstanceOf(AuthenticationError);
    const other = new SessionTokenService('a-different-secret-that-is-longer-than-thirty-two');
    const token = await other.create({ id: randomUUID(), displayName: 'Other' });
    await expect(service.verify(`Bearer ${token}`)).rejects.toBeInstanceOf(AuthenticationError);
  });
});
