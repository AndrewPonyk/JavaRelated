import type { VercelRequest, VercelResponse } from '@vercel/node';

/**
 * HTTP envelope helpers — every route speaks the same shapes:
 *   success: route-specific JSON
 *   failure: { error: { code, message } }   (never stack traces — ARCHITECTURE §2.6)
 */

export function sendError(
  res: VercelResponse,
  status: number,
  code: string,
  message: string,
): void {
  res.status(status).json({ error: { code, message } });
}

/** Returns false (and responds 405) when the method is not allowed. */
export function allowMethods(
  req: VercelRequest,
  res: VercelResponse,
  methods: readonly string[],
): boolean {
  if (req.method && methods.includes(req.method)) return true;
  res.setHeader('Allow', methods.join(', '));
  sendError(res, 405, 'METHOD_NOT_ALLOWED', `Use ${methods.join(' or ')}.`);
  return false;
}

/** Structured JSON log line — queryable in Vercel logs, drainable later. */
export function logError(route: string, error: unknown): void {
  console.error(
    JSON.stringify({
      level: 'error',
      route,
      message: error instanceof Error ? error.message : String(error),
    }),
  );
}
