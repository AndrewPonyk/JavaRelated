import { z } from 'zod';

import { paginationQuerySchema, polygonGeoJsonSchema } from './common.schema';

export const listFloorsQuerySchema = paginationQuerySchema.extend({
  venueId: z.string().uuid().optional(),
});

export const createFloorSchema = z.object({
  venueId: z.string().uuid(),
  level: z.number().int().min(-20).max(200),
  name: z.string().trim().min(1).max(120),
  floorPlan: polygonGeoJsonSchema.optional(),
  mapboxLayerId: z.string().trim().min(1).max(160).optional(),
});

export const updateFloorSchema = createFloorSchema
  .omit({ venueId: true })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export type ListFloorsQuery = z.infer<typeof listFloorsQuerySchema>;
export type CreateFloorInput = z.infer<typeof createFloorSchema>;
export type UpdateFloorInput = z.infer<typeof updateFloorSchema>;
