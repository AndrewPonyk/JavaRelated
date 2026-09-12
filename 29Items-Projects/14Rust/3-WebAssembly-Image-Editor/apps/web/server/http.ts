import { randomUUID } from 'node:crypto';

import { z } from 'zod';

import {
  anonymousSessionSchema,
  paginationSchema,
  presetCreateSchema,
  presetUpdateSchema,
  projectCreateSchema,
  projectUpdateSchema,
} from '../src/contracts/editor';
import { AuthenticationError } from './auth';
import type { SessionTokenService } from './auth';
import type { FixedWindowRateLimiter } from './rate-limit';
import {
  InvalidSessionError,
  ResourceNotFoundError,
  type PresetService,
  type ProjectService,
  type UserService,
} from './services';

export interface ApiRequest {
  readonly method: string;
  readonly path: string;
  readonly headers: Readonly<Record<string, string | undefined>>;
  readonly body?: unknown;
  readonly requestId?: string;
}

export interface ApiResponse {
  readonly status: number;
  readonly headers?: Readonly<Record<string, string>>;
  readonly body?: unknown;
}

export interface ApiDependencies {
  readonly users: UserService;
  readonly projects: ProjectService;
  readonly presets: PresetService;
  readonly tokens: SessionTokenService;
  readonly health: () => Promise<void>;
  readonly corsOrigin?: string;
  readonly rateLimiter?: FixedWindowRateLimiter;
}

function json(status: number, body?: unknown, headers: Record<string, string> = {}): ApiResponse {
  return {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      ...headers,
    },
    body,
  };
}

function error(
  status: number,
  code: string,
  message: string,
  requestId: string,
  headers: Record<string, string> = {},
): ApiResponse {
  return json(status, { error: { code, message, requestId } }, headers);
}

function responseHeaders(request: ApiRequest, corsOrigin?: string): Record<string, string> {
  const headers: Record<string, string> = { 'x-request-id': request.requestId ?? randomUUID() };
  if (corsOrigin && request.headers.origin === corsOrigin) {
    headers['access-control-allow-origin'] = corsOrigin;
    headers.vary = 'Origin';
  }
  return headers;
}

function route(path: string): readonly string[] {
  return new URL(path, 'http://api.local').pathname.split('/').filter(Boolean);
}

function query(path: string, name: string): string | undefined {
  return new URL(path, 'http://api.local').searchParams.get(name) ?? undefined;
}

function pagination(path: string) {
  return paginationSchema.parse({
    limit: query(path, 'limit'),
    offset: query(path, 'offset'),
  });
}

function databaseErrorCode(cause: unknown): string | undefined {
  if (!cause || typeof cause !== 'object' || !('code' in cause)) return undefined;
  return typeof cause.code === 'string' ? cause.code : undefined;
}

function noContent(headers: Record<string, string>): ApiResponse {
  return { status: 204, headers: { 'cache-control': 'no-store', ...headers } };
}

function methodNotAllowed(requestId: string, headers: Record<string, string>): ApiResponse {
  return error(
    405,
    'METHOD_NOT_ALLOWED',
    'HTTP method is not supported for this route.',
    requestId,
    { ...headers, allow: 'GET, POST, PUT, DELETE, OPTIONS' },
  );
}

export function createApiHandler(dependencies: ApiDependencies) {
  return async (request: ApiRequest): Promise<ApiResponse> => {
    const requestId = request.requestId ?? randomUUID();
    const commonHeaders = responseHeaders({ ...request, requestId }, dependencies.corsOrigin);
    if (request.method === 'OPTIONS') {
      return {
        status: 204,
        headers: {
          ...commonHeaders,
          'access-control-allow-methods': 'GET, POST, PUT, DELETE, OPTIONS',
          'access-control-allow-headers': 'authorization, content-type, x-request-id',
          'access-control-max-age': '600',
        },
      };
    }
    try {
      const segments = route(request.path);
      const exactRoute = segments.join('/');

      if (exactRoute === 'api/health') {
        if (request.method !== 'GET') return methodNotAllowed(requestId, commonHeaders);
        try {
          await dependencies.health();
          return json(200, { data: { status: 'ok' } }, commonHeaders);
        } catch {
          return error(
            503,
            'SERVICE_UNAVAILABLE',
            'The API is not ready.',
            requestId,
            commonHeaders,
          );
        }
      }

      const isAnonymousSession = exactRoute === 'api/auth/anonymous';
      const isCollection =
        segments[0] === 'api' &&
        (segments[1] === 'projects' || segments[1] === 'presets') &&
        segments.length <= 3;
      if (!isAnonymousSession && !isCollection) {
        return error(404, 'NOT_FOUND', 'Route was not found.', requestId, commonHeaders);
      }

      const rateKey =
        request.headers['x-forwarded-for']?.split(',')[0]?.trim() ||
        request.headers.authorization ||
        'anonymous';
      if (dependencies.rateLimiter && !dependencies.rateLimiter.allow(rateKey.slice(0, 256))) {
        return error(429, 'RATE_LIMITED', 'Too many requests. Please retry shortly.', requestId, {
          ...commonHeaders,
          'retry-after': '60',
        });
      }

      if (isAnonymousSession) {
        if (request.method !== 'POST') return methodNotAllowed(requestId, commonHeaders);
        const input = anonymousSessionSchema.parse(request.body ?? {});
        const user = await dependencies.users.create(
          randomUUID(),
          input.displayName ?? 'Anonymous editor',
        );
        const token = await dependencies.tokens.create({
          id: user.id,
          displayName: user.displayName,
        });
        return json(201, { data: { user, token } }, commonHeaders);
      }

      const actor = await dependencies.tokens.verify(request.headers.authorization);
      await dependencies.users.require(actor.id);

      const resource = segments[1];
      const id = segments[2];
      if (id && !z.string().uuid().safeParse(id).success) {
        return error(400, 'INVALID_INPUT', 'Resource id must be a UUID.', requestId, commonHeaders);
      }

      if (resource === 'projects') {
        if (!id && request.method === 'GET') {
          const page = pagination(request.path);
          const data = await dependencies.projects.list(actor.id, page);
          return json(200, { data, meta: { ...page, count: data.length } }, commonHeaders);
        }
        if (!id && request.method === 'POST') {
          const input = projectCreateSchema.parse(request.body);
          return json(
            201,
            { data: await dependencies.projects.create(actor.id, input.name) },
            commonHeaders,
          );
        }
        if (id && request.method === 'GET') {
          return json(200, { data: await dependencies.projects.get(actor.id, id) }, commonHeaders);
        }
        if (id && request.method === 'PUT') {
          const input = projectUpdateSchema.parse(request.body);
          return json(
            200,
            { data: await dependencies.projects.update(actor.id, id, input.name) },
            commonHeaders,
          );
        }
        if (id && request.method === 'DELETE') {
          await dependencies.projects.delete(actor.id, id);
          return noContent(commonHeaders);
        }
      }

      if (resource === 'presets') {
        if (!id && request.method === 'GET') {
          const projectId = query(request.path, 'projectId');
          if (projectId && !z.string().uuid().safeParse(projectId).success) {
            return error(
              400,
              'INVALID_INPUT',
              'projectId must be a UUID.',
              requestId,
              commonHeaders,
            );
          }
          const page = pagination(request.path);
          const data = await dependencies.presets.list(actor.id, page, projectId);
          return json(200, { data, meta: { ...page, count: data.length } }, commonHeaders);
        }
        if (!id && request.method === 'POST') {
          const input = presetCreateSchema.parse(request.body);
          return json(
            201,
            { data: await dependencies.presets.create(actor.id, input) },
            commonHeaders,
          );
        }
        if (id && request.method === 'GET') {
          return json(200, { data: await dependencies.presets.get(actor.id, id) }, commonHeaders);
        }
        if (id && request.method === 'PUT') {
          const input = presetUpdateSchema.parse(request.body);
          return json(
            200,
            { data: await dependencies.presets.update(actor.id, id, input) },
            commonHeaders,
          );
        }
        if (id && request.method === 'DELETE') {
          await dependencies.presets.delete(actor.id, id);
          return noContent(commonHeaders);
        }
      }

      return methodNotAllowed(requestId, commonHeaders);
    } catch (cause) {
      if (cause instanceof z.ZodError) {
        return json(
          400,
          {
            error: {
              code: 'INVALID_INPUT',
              message: 'Request validation failed.',
              issues: cause.issues.map(({ path, message }) => ({ path, message })),
              requestId,
            },
          },
          commonHeaders,
        );
      }
      if (cause instanceof AuthenticationError) {
        return error(401, 'UNAUTHENTICATED', cause.message, requestId, commonHeaders);
      }
      if (cause instanceof InvalidSessionError) {
        return error(401, 'UNAUTHENTICATED', cause.message, requestId, commonHeaders);
      }
      if (cause instanceof ResourceNotFoundError) {
        return error(404, 'NOT_FOUND', cause.message, requestId, commonHeaders);
      }
      const databaseCode = databaseErrorCode(cause);
      if (databaseCode === '23505') {
        return error(
          409,
          'CONFLICT',
          'A resource with that name already exists.',
          requestId,
          commonHeaders,
        );
      }
      if (databaseCode === '23503' || databaseCode === '23514') {
        return error(
          400,
          'INVALID_INPUT',
          'The request references invalid or inconsistent data.',
          requestId,
          commonHeaders,
        );
      }
      console.error(
        JSON.stringify({
          level: 'error',
          requestId,
          message: 'API request failed',
          cause: cause instanceof Error ? cause.name : 'UnknownError',
        }),
      );
      return error(
        500,
        'INTERNAL_ERROR',
        'The request could not be completed.',
        requestId,
        commonHeaders,
      );
    }
  };
}
