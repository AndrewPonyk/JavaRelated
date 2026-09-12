import { randomUUID } from 'node:crypto';
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';

import { createApplication } from './application';

const application = createApplication();

class RequestBodyError extends Error {
  constructor(
    readonly status: 400 | 413 | 415,
    readonly code: string,
  ) {
    super(code);
  }
}

async function readJsonBody(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += buffer.length;
    if (size > 32_000) throw new RequestBodyError(413, 'PAYLOAD_TOO_LARGE');
    chunks.push(buffer);
  }
  if (chunks.length === 0) return undefined;
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8')) as unknown;
  } catch {
    throw new RequestBodyError(400, 'INVALID_JSON');
  }
}

function headers(request: IncomingMessage): Record<string, string | undefined> {
  return Object.fromEntries(
    Object.entries(request.headers).map(([key, value]) => [
      key,
      Array.isArray(value) ? value[0] : value,
    ]),
  );
}

async function handle(request: IncomingMessage, response: ServerResponse): Promise<void> {
  try {
    const hasBody = ['POST', 'PUT'].includes(request.method ?? '');
    if (hasBody && !request.headers['content-type']?.toLowerCase().startsWith('application/json')) {
      throw new RequestBodyError(415, 'UNSUPPORTED_MEDIA_TYPE');
    }
    const body = hasBody ? await readJsonBody(request) : undefined;
    const requestHeaders = headers(request);
    const result = await application.handle({
      method: request.method ?? 'GET',
      path: request.url ?? '/',
      headers: requestHeaders,
      body,
      requestId: requestHeaders['x-request-id'],
    });
    response.writeHead(result.status, result.headers);
    response.end(result.body === undefined ? undefined : JSON.stringify(result.body));
  } catch (cause) {
    const status = cause instanceof RequestBodyError ? cause.status : 400;
    const code = cause instanceof RequestBodyError ? cause.code : 'INVALID_REQUEST';
    const incomingRequestId = request.headers['x-request-id'];
    const requestId = typeof incomingRequestId === 'string' ? incomingRequestId : randomUUID();
    response.writeHead(status, {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-request-id': requestId,
    });
    response.end(
      JSON.stringify({
        error: {
          code,
          message:
            status === 413
              ? 'Request body is too large.'
              : status === 415
                ? 'Content-Type must be application/json.'
                : 'Request body is not valid JSON.',
          requestId,
        },
      }),
    );
  }
}

const server = createServer((request, response) => void handle(request, response));
server.listen(application.config.PORT, () => {
  console.info(
    JSON.stringify({ level: 'info', message: `API listening on ${application.config.PORT}` }),
  );
});

let shuttingDown = false;

async function shutdown() {
  if (shuttingDown) return;
  shuttingDown = true;
  await new Promise<void>((resolve, reject) =>
    server.close((error) => (error ? reject(error) : resolve())),
  );
  await application.database.close();
}

function requestShutdown(): void {
  void shutdown().catch((cause) => {
    console.error(
      JSON.stringify({
        level: 'error',
        message: 'API shutdown failed',
        cause: cause instanceof Error ? cause.name : 'UnknownError',
      }),
    );
    process.exitCode = 1;
  });
}

process.once('SIGTERM', requestShutdown);
process.once('SIGINT', requestShutdown);
