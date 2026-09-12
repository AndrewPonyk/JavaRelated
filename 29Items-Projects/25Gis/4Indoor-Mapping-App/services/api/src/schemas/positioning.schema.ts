import { z } from 'zod';

import { jsonRecordSchema, paginationQuerySchema } from './common.schema';
import { wifiReadingSchema } from './wifi.schema';

export const beaconReadingSchema = z.object({
  provider: z.string().trim().min(1).max(80),
  externalId: z.string().trim().min(1).max(160),
  rssi: z.number().min(-120).max(0).optional(),
});

export const listPositioningEventsQuerySchema = paginationQuerySchema.extend({
  venueId: z.string().uuid().optional(),
  floorId: z.string().uuid().optional(),
  userHash: z.string().trim().min(1).max(160).optional(),
});

export const createPositioningEventSchema = z.object({
  venueId: z.string().uuid(),
  floorId: z.string().uuid().optional(),
  userHash: z.string().trim().min(1).max(160).optional(),
  latitude: z.number().min(-90).max(90).optional(),
  longitude: z.number().min(-180).max(180).optional(),
  confidence: z.number().min(0).max(1).optional(),
  rawSignalSummary: jsonRecordSchema,
  observedAt: z.string().datetime().optional(),
});

export const updatePositioningEventSchema = createPositioningEventSchema
  .omit({ venueId: true })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export const ingestPositioningEventSchema = z.object({
  venueId: z.string().uuid(),
  floorId: z.string().uuid().optional(),
  userHash: z.string().trim().min(1).max(160).optional(),
  wifiReadings: z.array(wifiReadingSchema).max(250).default([]),
  beaconReadings: z.array(beaconReadingSchema).max(100).default([]),
  observedAt: z.string().datetime().optional(),
});

export type BeaconReading = z.infer<typeof beaconReadingSchema>;
export type ListPositioningEventsQuery = z.infer<typeof listPositioningEventsQuerySchema>;
export type CreatePositioningEventInput = z.infer<typeof createPositioningEventSchema>;
export type UpdatePositioningEventInput = z.infer<typeof updatePositioningEventSchema>;
export type IngestPositioningEventInput = z.infer<typeof ingestPositioningEventSchema>;
