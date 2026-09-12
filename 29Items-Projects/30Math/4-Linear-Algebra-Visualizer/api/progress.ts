import type { VercelRequest, VercelResponse } from '@vercel/node';

import { allowMethods, logError, sendError } from './_lib/http';
import { getProgress } from './_lib/progressRepo';
import { ProgressQuerySchema } from './_lib/schemas';

/**
 * GET /api/progress?deviceId=…
 * → 200 { progress: [{ topic, mastery, attempts }] }   (empty array for unknown devices)
 */
export default async function handler(req: VercelRequest, res: VercelResponse): Promise<void> {
  if (!allowMethods(req, res, ['GET'])) return;

  const parsed = ProgressQuerySchema.safeParse({ deviceId: req.query.deviceId });
  if (!parsed.success) {
    sendError(res, 400, 'VALIDATION_ERROR', 'deviceId query parameter is required (8–64 chars).');
    return;
  }

  try {
    const progress = await getProgress(parsed.data.deviceId);
    res.status(200).json({ progress });
  } catch (error) {
    logError('/api/progress', error);
    sendError(res, 500, 'INTERNAL', 'Failed to load progress.');
  }
}
