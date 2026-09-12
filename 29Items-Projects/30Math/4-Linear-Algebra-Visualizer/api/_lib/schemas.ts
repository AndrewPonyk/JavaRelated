import { z } from 'zod';

/**
 * Input validation at the trust boundary — nothing from the wire is used
 * before passing one of these schemas.
 *
 * NOTE: the topic slugs deliberately DUPLICATE src/core/exercises/types.ts
 * (5 lines) instead of sharing a package — api/ and src/ stay decoupled until
 * drift actually hurts (PROJECT-PLAN §1.1).
 */

export const TopicSchema = z.enum([
  'vectors',
  'linear-combinations',
  'transformations',
  'determinant',
  'eigenvalues',
]);

export type TopicSlug = z.infer<typeof TopicSchema>;

export const DeviceIdSchema = z.string().min(8).max(64);

export const AttemptInputSchema = z.object({
  /** Client-generated idempotency key — retries must not double-count. */
  attemptId: z.string().uuid(),
  deviceId: DeviceIdSchema,
  topic: TopicSchema,
  difficulty: z.number().int().min(1).max(5),
  seed: z.number().int().nonnegative(),
  correct: z.boolean(),
  durationMs: z.number().int().nonnegative().max(3_600_000),
  // Mastery is deliberately NOT accepted from the client — the server
  // recomputes it from the correctness bit (progressRepo + mastery.ts).
});

export type AttemptInput = z.infer<typeof AttemptInputSchema>;

export const ProgressQuerySchema = z.object({
  deviceId: DeviceIdSchema,
});
