import { z } from 'zod';

// Shared API schemas. Feature-specific schemas live in their slice's *.api.ts.
// All of these must track contracts/openapi.yaml.

export const problemSchema = z.object({
  code: z.string(),
  message: z.string(),
  details: z.unknown().optional(),
});
export type Problem = z.infer<typeof problemSchema>;

export const announcementSchema = z.object({
  id: z.string(),
  title: z.string(),
  body: z.string(),
  publishedAt: z.string(), // ISO-8601; rendered via <time> + Intl
});
export type Announcement = z.infer<typeof announcementSchema>;

export const announcementListSchema = z.array(announcementSchema);
