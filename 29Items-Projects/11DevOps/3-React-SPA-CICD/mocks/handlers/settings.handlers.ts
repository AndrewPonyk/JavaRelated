import { http, HttpResponse } from 'msw';
import { z } from 'zod';

import { db } from '../db/store';

// PATCH validation mirrors contracts/openapi.yaml → ProfilePatch ("server never trusts client").
const profilePatchSchema = z
  .object({
    displayName: z.string().min(1).max(80),
    marketingEmails: z.boolean(),
    productUpdates: z.boolean(),
  })
  .partial()
  .refine((patch) => Object.keys(patch).length > 0, {
    message: 'At least one field is required',
  });

export const settingsHandlers = [
  http.get('*/v1/settings/profile', () => {
    return HttpResponse.json(db.profile);
  }),

  http.patch('*/v1/settings/profile', async ({ request }) => {
    const parsed = profilePatchSchema.safeParse(await request.json().catch(() => null));
    if (!parsed.success) {
      return HttpResponse.json(
        {
          code: 'VALIDATION_ERROR',
          message: 'Invalid profile patch',
          details: parsed.error.flatten(),
        },
        { status: 400 },
      );
    }

    db.profile = { ...db.profile, ...parsed.data };
    return HttpResponse.json(db.profile);
  }),
];
