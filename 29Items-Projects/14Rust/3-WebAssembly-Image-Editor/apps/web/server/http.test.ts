// @vitest-environment node

import { randomUUID } from 'node:crypto';

import { DataType, newDb } from 'pg-mem';
import { describe, expect, it } from 'vitest';

import { SessionTokenService } from './auth';
import { Database, type TransactionalPool } from './database';
import { createApiHandler } from './http';
import { FixedWindowRateLimiter } from './rate-limit';
import { PresetRepository, ProjectRepository, UserRepository } from './repositories';
import { PresetService, ProjectService, UserService } from './services';

const tokenSecret = 'a-secure-test-secret-with-more-than-thirty-two-characters';

async function harness(
  options: {
    health?: () => Promise<void>;
    rateLimiter?: FixedWindowRateLimiter;
    projectListError?: Error;
  } = {},
) {
  const memory = newDb({ autoCreateForeignKeyIndices: true });
  memory.public.registerFunction({
    name: 'gen_random_uuid',
    returns: DataType.uuid,
    implementation: randomUUID,
    impure: true,
  });
  memory.public.none(`
    CREATE TABLE editor_users (id uuid PRIMARY KEY, display_name text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
    CREATE TABLE editor_projects (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_id uuid NOT NULL, name text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(owner_id, name));
    CREATE TABLE edit_presets (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_id uuid NOT NULL, project_id uuid NULL, name text NOT NULL, recipe jsonb NOT NULL, schema_version smallint NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), UNIQUE(owner_id, name));
  `);
  const adapter = memory.adapters.createPg();
  const pool = new adapter.Pool();
  const database = new Database(pool as unknown as TransactionalPool, false);
  const users = new UserService(new UserRepository(database));
  const projects = new ProjectService(new ProjectRepository(database));
  if (options.projectListError) {
    projects.list = () => Promise.reject(options.projectListError);
  }
  const presets = new PresetService(new PresetRepository(database));
  return createApiHandler({
    users,
    projects,
    presets,
    tokens: new SessionTokenService(tokenSecret),
    health: options.health ?? (async () => void (await database.query('SELECT 1'))),
    rateLimiter: options.rateLimiter,
  });
}

async function session(handler: Awaited<ReturnType<typeof harness>>) {
  const response = await handler({
    method: 'POST',
    path: '/api/auth/anonymous',
    headers: {},
    body: { displayName: 'Test editor' },
  });
  expect(response.status).toBe(201);
  const token = (response.body as { data: { token: string } }).data.token;
  return { authorization: `Bearer ${token}` };
}

describe('API integration', () => {
  it('creates, reads, updates, and deletes owner-scoped projects and presets', async () => {
    const handler = await harness();
    const headers = await session(handler);
    const project = await handler({
      method: 'POST',
      path: '/api/projects',
      headers,
      body: { name: 'Summer edits' },
    });
    expect(project.status).toBe(201);
    const projectId = (project.body as { data: { id: string } }).data.id;

    const preset = await handler({
      method: 'POST',
      path: '/api/presets',
      headers,
      body: {
        name: 'Warm',
        projectId,
        recipe: { schemaVersion: 1, filters: [{ name: 'sepia', amount: 0 }] },
      },
    });
    expect(preset.status).toBe(201);
    const presetId = (preset.body as { data: { id: string } }).data.id;

    const listed = await handler({
      method: 'GET',
      path: `/api/presets?projectId=${projectId}`,
      headers,
    });
    expect(listed.status).toBe(200);
    expect((listed.body as { data: readonly { id: string }[] }).data).toHaveLength(1);

    const updated = await handler({
      method: 'PUT',
      path: `/api/presets/${presetId}`,
      headers,
      body: { name: 'Warm contrast' },
    });
    expect((updated.body as { data: { name: string } }).data.name).toBe('Warm contrast');

    expect(
      (await handler({ method: 'DELETE', path: `/api/presets/${presetId}`, headers })).status,
    ).toBe(204);
    expect(
      (await handler({ method: 'DELETE', path: `/api/projects/${projectId}`, headers })).status,
    ).toBe(204);
  });

  it('rejects invalid and unauthenticated requests without exposing records', async () => {
    const handler = await harness();
    expect((await handler({ method: 'GET', path: '/api/health', headers: {} })).status).toBe(200);
    expect((await handler({ method: 'OPTIONS', path: '/api/projects', headers: {} })).status).toBe(
      204,
    );
    expect((await handler({ method: 'GET', path: '/api/projects', headers: {} })).status).toBe(401);
    const headers = await session(handler);
    const invalid = await handler({
      method: 'POST',
      path: '/api/presets',
      headers,
      body: { name: '', recipe: {} },
    });
    expect(invalid.status).toBe(400);
    const unknown = await handler({ method: 'GET', path: `/api/presets/${randomUUID()}`, headers });
    expect(unknown.status).toBe(404);
    expect(
      (await handler({ method: 'GET', path: '/api/presets?projectId=not-a-uuid', headers })).status,
    ).toBe(400);
    expect((await handler({ method: 'PATCH', path: '/api/projects', headers })).status).toBe(405);
    expect(
      (await handler({ method: 'GET', path: '/api/projects/not-a-uuid', headers })).status,
    ).toBe(400);
    expect(
      (await handler({ method: 'GET', path: `/api/projects/${randomUUID()}/extra`, headers }))
        .status,
    ).toBe(404);
    expect((await handler({ method: 'GET', path: '/api/unknown', headers: {} })).status).toBe(404);
    expect(
      (await handler({ method: 'GET', path: '/api/projects?limit=0&offset=-1', headers })).status,
    ).toBe(400);
  });

  it('paginates collections and maps duplicate names to a conflict', async () => {
    const handler = await harness();
    const headers = await session(handler);
    for (const name of ['First', 'Second', 'Third']) {
      expect(
        (await handler({ method: 'POST', path: '/api/projects', headers, body: { name } })).status,
      ).toBe(201);
    }
    const page = await handler({
      method: 'GET',
      path: '/api/projects?limit=1&offset=1',
      headers,
    });
    expect(page.status).toBe(200);
    expect((page.body as { data: unknown[] }).data).toHaveLength(1);
    expect((page.body as { meta: { limit: number; offset: number; count: number } }).meta).toEqual({
      limit: 1,
      offset: 1,
      count: 1,
    });
    const conflict = await handler({
      method: 'POST',
      path: '/api/projects',
      headers,
      body: { name: 'First' },
    });
    expect(conflict.status).toBe(409);
  });

  it('reports failed readiness and expired application sessions safely', async () => {
    const unhealthy = await harness({ health: () => Promise.reject(new Error('database down')) });
    expect((await unhealthy({ method: 'GET', path: '/api/health', headers: {} })).status).toBe(503);

    const handler = await harness();
    const token = await new SessionTokenService(tokenSecret).create({
      id: randomUUID(),
      displayName: 'Missing user',
    });
    const response = await handler({
      method: 'GET',
      path: '/api/projects',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(response.status).toBe(401);
  });

  it('returns rate-limit and unexpected-error envelopes without leaking internals', async () => {
    const limited = await harness({ rateLimiter: new FixedWindowRateLimiter(1) });
    const first = await limited({
      method: 'POST',
      path: '/api/auth/anonymous',
      headers: { 'x-forwarded-for': '192.0.2.1' },
      body: {},
    });
    expect(first.status).toBe(201);
    const blocked = await limited({
      method: 'POST',
      path: '/api/auth/anonymous',
      headers: { 'x-forwarded-for': '192.0.2.1' },
      body: {},
    });
    expect(blocked.status).toBe(429);
    expect(blocked.headers?.['retry-after']).toBe('60');

    const failure = await harness({ projectListError: new Error('sensitive database detail') });
    const headers = await session(failure);
    const log = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const response = await failure({ method: 'GET', path: '/api/projects', headers });
    expect(response.status).toBe(500);
    expect(JSON.stringify(response.body)).not.toContain('sensitive database detail');
    expect(log).toHaveBeenCalledOnce();
  });
});
