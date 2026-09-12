import type { VercelRequest, VercelResponse } from '@vercel/node';

/** Minimal req/res doubles for exercising Vercel function handlers directly. */

export function createMockReq(
  overrides: Partial<{
    method: string;
    body: unknown;
    query: Record<string, string | string[]>;
    headers: Record<string, string | string[]>;
  }> = {},
): VercelRequest {
  return {
    method: 'GET',
    body: undefined,
    query: {},
    headers: {},
    ...overrides,
  } as unknown as VercelRequest;
}

export interface MockRes {
  statusCode: number | null;
  jsonBody: unknown;
  headers: Record<string, string>;
  res: VercelResponse;
}

export function createMockRes(): MockRes {
  const state = {
    statusCode: null,
    jsonBody: null,
    headers: {},
  } as unknown as MockRes;

  const res = {
    status(code: number) {
      state.statusCode = code;
      return res;
    },
    json(body: unknown) {
      state.jsonBody = body;
      return res;
    },
    setHeader(name: string, value: string) {
      state.headers[name] = value;
      return res;
    },
  };

  state.res = res as unknown as VercelResponse;
  return state;
}
