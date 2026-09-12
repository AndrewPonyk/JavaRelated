import { z } from 'zod';

export const uuidParamsSchema = z.object({
  id: z.string().uuid(),
});

export const optionalUuidQuerySchema = z.object({
  venueId: z.string().uuid().optional(),
  floorId: z.string().uuid().optional(),
});

export const paginationQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(200).default(50),
});

const longitudeLatitudeSchema = z.tuple([
  z.number().min(-180).max(180),
  z.number().min(-90).max(90),
]);

const polygonRingSchema = z.array(longitudeLatitudeSchema).min(4);

export const polygonGeoJsonSchema = z.object({
  type: z.literal('Polygon'),
  coordinates: z.array(polygonRingSchema).min(1),
});

export const lineStringGeoJsonSchema = z.object({
  type: z.literal('LineString'),
  coordinates: z.array(longitudeLatitudeSchema).min(2),
});

export const jsonRecordSchema = z.record(z.unknown()).optional().default({});

export type PolygonGeoJson = z.infer<typeof polygonGeoJsonSchema>;
export type LineStringGeoJson = z.infer<typeof lineStringGeoJsonSchema>;
