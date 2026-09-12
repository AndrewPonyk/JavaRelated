import { z } from 'zod';

import { paginationQuerySchema } from './common.schema';

export const listPoiQuerySchema = paginationQuerySchema.extend({
  venueId: z.string().uuid(),
  floorId: z.string().uuid().optional(),
  query: z.string().trim().min(1).max(120).optional(),
  category: z.string().trim().min(1).max(80).optional(),
  latitude: z.coerce.number().min(-90).max(90).optional(),
  longitude: z.coerce.number().min(-180).max(180).optional(),
});

export const poiIdParamsSchema = z.object({
  id: z.string().uuid(),
});

export const createPoiSchema = z.object({
  venueId: z.string().uuid(),
  floorId: z.string().uuid(),
  name: z.string().trim().min(1).max(160),
  category: z.string().trim().min(1).max(80),
  description: z.string().trim().max(500).optional(),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
});

export const updatePoiSchema = createPoiSchema
  .omit({ venueId: true })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export type ListPoiQuery = z.infer<typeof listPoiQuerySchema>;
export type CreatePoiInput = z.infer<typeof createPoiSchema>;
export type UpdatePoiInput = z.infer<typeof updatePoiSchema>;
