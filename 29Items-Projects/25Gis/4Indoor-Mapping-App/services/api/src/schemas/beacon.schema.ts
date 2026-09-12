import { z } from 'zod';

import { jsonRecordSchema, paginationQuerySchema } from './common.schema';

export const listBeaconAnchorsQuerySchema = paginationQuerySchema.extend({
  venueId: z.string().uuid().optional(),
  floorId: z.string().uuid().optional(),
  provider: z.string().trim().min(1).max(80).optional(),
});

export const createBeaconAnchorSchema = z.object({
  venueId: z.string().uuid(),
  floorId: z.string().uuid(),
  provider: z.string().trim().min(1).max(80),
  externalId: z.string().trim().min(1).max(160),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  metadata: jsonRecordSchema,
});

export const updateBeaconAnchorSchema = createBeaconAnchorSchema
  .omit({ venueId: true })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export type ListBeaconAnchorsQuery = z.infer<typeof listBeaconAnchorsQuerySchema>;
export type CreateBeaconAnchorInput = z.infer<typeof createBeaconAnchorSchema>;
export type UpdateBeaconAnchorInput = z.infer<typeof updateBeaconAnchorSchema>;
