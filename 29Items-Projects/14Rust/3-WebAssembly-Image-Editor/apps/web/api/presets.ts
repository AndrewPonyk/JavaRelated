import { createApplication } from '../server/application';

interface VercelRequest {
  readonly method?: string;
  readonly url?: string;
  readonly body?: unknown;
  readonly headers: Record<string, string | string[] | undefined>;
}

interface VercelResponse {
  setHeader(name: string, value: string): void;
  status(statusCode: number): VercelResponse;
  json(body: unknown): void;
  end(): void;
}

let application: ReturnType<typeof createApplication> | undefined;

function normalizedHeaders(headers: VercelRequest['headers']): Record<string, string | undefined> {
  return Object.fromEntries(
    Object.entries(headers).map(([key, value]) => [
      key.toLowerCase(),
      Array.isArray(value) ? value[0] : value,
    ]),
  );
}

export default async function handler(
  request: VercelRequest,
  response: VercelResponse,
): Promise<void> {
  try {
    application ??= createApplication();
    const headers = normalizedHeaders(request.headers);
    const result = await application.handle({
      method: request.method ?? 'GET',
      path: request.url ?? '/api/presets',
      headers,
      body: request.body,
      requestId: headers['x-vercel-id'] ?? headers['x-request-id'],
    });
    for (const [name, value] of Object.entries(result.headers ?? {}))
      response.setHeader(name, value);
    if (result.status === 204) {
      response.status(204).end();
      return;
    }
    response.status(result.status).json(result.body ?? {});
  } catch {
    response
      .status(503)
      .json({ error: { code: 'SERVICE_UNAVAILABLE', message: 'The API is unavailable.' } });
  }
}
