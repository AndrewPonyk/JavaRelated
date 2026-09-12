import { z } from 'zod';

import { lineStringGeoJsonSchema, paginationQuerySchema } from './common.schema';

export const listRouteNodesQuerySchema = paginationQuerySchema.extend({
  venueId: z.string().uuid().optional(),
  floorId: z.string().uuid().optional(),
  nodeType: z.string().trim().min(1).max(80).optional(),
});

export const createRouteNodeSchema = z.object({
  venueId: z.string().uuid(),
  floorId: z.string().uuid(),
  nodeType: z.string().trim().min(1).max(80).default('walkway'),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
});

export const updateRouteNodeSchema = createRouteNodeSchema
  .omit({ venueId: true })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export const listRouteEdgesQuerySchema = paginationQuerySchema.extend({
  venueId: z.string().uuid().optional(),
  fromNodeId: z.string().uuid().optional(),
  toNodeId: z.string().uuid().optional(),
  accessibleOnly: z.coerce.boolean().optional(),
});

export const createRouteEdgeSchema = z.object({
  venueId: z.string().uuid(),
  fromNodeId: z.string().uuid(),
  toNodeId: z.string().uuid(),
  travelCost: z.number().positive(),
  isAccessible: z.boolean().default(true),
  geometry: lineStringGeoJsonSchema,
});

export const updateRouteEdgeSchema = createRouteEdgeSchema
  .omit({ venueId: true })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export const directionsQuerySchema = z.object({
  venueId: z.string().uuid(),
  fromNodeId: z.string().uuid(),
  toNodeId: z.string().uuid(),
  accessibleOnly: z.coerce.boolean().default(false),
});

export type ListRouteNodesQuery = z.infer<typeof listRouteNodesQuerySchema>;
export type CreateRouteNodeInput = z.infer<typeof createRouteNodeSchema>;
export type UpdateRouteNodeInput = z.infer<typeof updateRouteNodeSchema>;
export type ListRouteEdgesQuery = z.infer<typeof listRouteEdgesQuerySchema>;
export type CreateRouteEdgeInput = z.infer<typeof createRouteEdgeSchema>;
export type UpdateRouteEdgeInput = z.infer<typeof updateRouteEdgeSchema>;
export type DirectionsQuery = z.infer<typeof directionsQuerySchema>;
