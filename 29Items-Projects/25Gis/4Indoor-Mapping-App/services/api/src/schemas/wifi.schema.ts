import { z } from 'zod';

import { paginationQuerySchema } from './common.schema';

export const wifiReadingSchema = z.object({
  bssid: z.string().trim().min(1).max(80),
  rssi: z.number().min(-120).max(0),
});

export const listWifiFingerprintsQuerySchema = paginationQuerySchema.extend({
  venueId: z.string().uuid().optional(),
  floorId: z.string().uuid().optional(),
});

export const createWifiFingerprintSchema = z.object({
  venueId: z.string().uuid(),
  floorId: z.string().uuid(),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  scan: z.array(wifiReadingSchema).min(1).max(250),
  collectedAt: z.string().datetime().optional(),
});

export const updateWifiFingerprintSchema = createWifiFingerprintSchema
  .omit({ venueId: true })
  .partial()
  .refine((value) => Object.keys(value).length > 0, {
    message: 'At least one field is required.',
  });

export type WifiReading = z.infer<typeof wifiReadingSchema>;
export type ListWifiFingerprintsQuery = z.infer<typeof listWifiFingerprintsQuerySchema>;
export type CreateWifiFingerprintInput = z.infer<typeof createWifiFingerprintSchema>;
export type UpdateWifiFingerprintInput = z.infer<typeof updateWifiFingerprintSchema>;
