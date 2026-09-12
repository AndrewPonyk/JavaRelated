import type { VercelRequest, VercelResponse } from '@vercel/node';

/** GET /api/health — liveness probe (no DB touch: cheap and dependency-free). */
export default function handler(_req: VercelRequest, res: VercelResponse): void {
  res.status(200).json({
    ok: true,
    service: 'linear-algebra-visualizer-api',
    timestamp: new Date().toISOString(),
  });
}
