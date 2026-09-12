import { z } from 'zod';

import { paginationQuerySchema, polygonGeoJsonSchema } from './common.schema';

export const venueTypeSchema = z.enum(['mall', 'airport']);

export const listVenuesQuerySchema = paginationQuerySchema.extend({
  query: z.string().trim().min(1).max(120).optional(),
  venueType: venueTypeSchema.optional(),
});

export const createVenueSchema = z.object({
  name: z.string().trim().min(1).max(160),
  venueType: venueTypeSchema,
  timezone: z.string().trim().min(1).max(80),
  boundary: polygonGeoJsonSchema.optional(),
});

export const updateVenueSchema = createVenueSchema
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export type ListVenuesQuery = z.infer<typeof listVenuesQuerySchema>;
export type CreateVenueInput = z.infer<typeof createVenueSchema>;
export type UpdateVenueInput = z.infer<typeof updateVenueSchema>;
