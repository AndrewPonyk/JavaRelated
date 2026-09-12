import type { VercelRequest, VercelResponse } from '@vercel/node';

import { allowMethods, logError, sendError } from './_lib/http';
import { recordAttempt } from './_lib/progressRepo';
import { checkRateLimit } from './_lib/rateLimit';
import { AttemptInputSchema } from './_lib/schemas';

/**
 * POST /api/attempts — record one graded exercise attempt.
 * → 201 { ok: true, progress: { topic, mastery, attempts } }  (server-computed mastery)
 * → 400 validation error | 429 rate limited | 500 internal
 */
export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  if (!allowMethods(req, res, ['POST'])) return;

  const parsed = AttemptInputSchema.safeParse(req.body);
  if (!parsed.success) {
    const detail = parsed.error.issues
      .map((issue) => `${issue.path.join('.')}: ${issue.message}`)
      .join('; ');
    sendError(res, 400, 'VALIDATION_ERROR', detail);
    return;
  }

  const ip = firstForwardedIp(req) ?? 'unknown';
  if (!checkRateLimit(`${ip}:${parsed.data.deviceId}`)) {
    sendError(res, 429, 'RATE_LIMITED', 'Too many attempts — slow down and try again shortly.');
    return;
  }

  try {
    const progress = await recordAttempt(parsed.data);
    res.status(201).json({ ok: true, progress });
  } catch (error) {
    logError('/api/attempts', error);
    sendError(res, 500, 'INTERNAL', 'Failed to record attempt.');
  }
}

function firstForwardedIp(req: VercelRequest): string | null {
  const header = req.headers['x-forwarded-for'];
  const raw = Array.isArray(header) ? header[0] : header;
  return raw ? raw.split(',')[0].trim() : null;
}
