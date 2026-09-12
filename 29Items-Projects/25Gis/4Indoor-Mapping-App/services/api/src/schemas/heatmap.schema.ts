import { z } from 'zod';

import { paginationQuerySchema, polygonGeoJsonSchema } from './common.schema';

export const listHeatmapCellsQuerySchema = paginationQuerySchema.extend({
  venueId: z.string().uuid().optional(),
  floorId: z.string().uuid().optional(),
  latestOnly: z.coerce.boolean().default(false),
});

export const createHeatmapCellSchema = z.object({
  venueId: z.string().uuid(),
  floorId: z.string().uuid(),
  cell: polygonGeoJsonSchema,
  densityScore: z.number().min(0),
  calculatedAt: z.string().datetime().optional(),
});

export const updateHeatmapCellSchema = createHeatmapCellSchema
  .omit({ venueId: true })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export type ListHeatmapCellsQuery = z.infer<typeof listHeatmapCellsQuerySchema>;
export type CreateHeatmapCellInput = z.infer<typeof createHeatmapCellSchema>;
export type UpdateHeatmapCellInput = z.infer<typeof updateHeatmapCellSchema>;
