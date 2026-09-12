import { http, HttpResponse } from 'msw';
import { z } from 'zod';

import { db } from '../db/store';

// Tracks contracts/openapi.yaml → GET /v1/activity?limit=
const activityQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(20),
});

export const dashboardHandlers = [
  http.get('*/v1/dashboard/summary', () => {
    // Auth is asserted loosely in mocks: the /me handler covers the strict path.
    return HttpResponse.json(db.dashboardSummary);
  }),

  http.get('*/v1/announcements', () => {
    return HttpResponse.json(db.announcements);
  }),

  http.get('*/v1/activity', ({ request }) => {
    const url = new URL(request.url);
    const parsed = activityQuerySchema.safeParse({
      limit: url.searchParams.get('limit') ?? undefined,
    });
    if (!parsed.success) {
      return HttpResponse.json(
        {
          code: 'VALIDATION_ERROR',
          message: 'Invalid limit parameter',
          details: parsed.error.flatten(),
        },
        { status: 400 },
      );
    }
    return HttpResponse.json(db.activity.slice(0, parsed.data.limit));
  }),
];
