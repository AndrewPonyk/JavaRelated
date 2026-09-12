import { z } from 'zod';

const configurationSchema = z.object({
  DATABASE_URL: z.string().url(),
  JWT_SECRET: z.string().min(32),
  PORT: z.coerce.number().int().positive().max(65535).default(3000),
  CORS_ORIGIN: z.string().url().optional(),
  PRESET_API_RATE_LIMIT_PER_MINUTE: z.coerce.number().int().positive().max(1000).default(60),
});

export type RuntimeConfig = z.infer<typeof configurationSchema>;

export function readRuntimeConfig(environment: NodeJS.ProcessEnv = process.env): RuntimeConfig {
  return configurationSchema.parse(environment);
}
