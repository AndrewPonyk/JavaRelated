import { z } from 'zod';

import { api } from '@/api/httpClient';

// Tracks contracts/openapi.yaml → Profile / ProfilePatch.
export const profileSchema = z.object({
  displayName: z.string().min(1).max(80),
  marketingEmails: z.boolean(),
  productUpdates: z.boolean(),
});
export type Profile = z.infer<typeof profileSchema>;

export const profilePatchSchema = profileSchema
  .partial()
  .refine((patch) => Object.keys(patch).length > 0, { message: 'At least one field must change' });
export type ProfilePatch = z.infer<typeof profilePatchSchema>;

export async function updateProfile(patch: ProfilePatch): Promise<Profile> {
  // Validate the outbound payload too — a malformed PATCH should fail here, loudly,
  // not as a mysterious 400 in production telemetry.
  const validated = profilePatchSchema.parse(patch);
  return api.patch('/v1/settings/profile', validated, { schema: profileSchema });
}
