import dotenv from 'dotenv';
import { z } from 'zod';

dotenv.config();

const booleanEnvSchema = z
  .union([z.boolean(), z.enum(['true', 'false'])])
  .default('false')
  .transform((value) => value === true || value === 'true');

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'staging', 'production']).default('development'),
  PORT: z.coerce.number().int().min(1).max(65_535).default(8080),
  DATABASE_URL: z.string().min(1, 'DATABASE_URL is required.'),
  LOG_LEVEL: z.string().min(1).default('info'),
  CORS_ORIGINS: z
    .string()
    .optional()
    .transform(
      (value) =>
        value
          ?.split(',')
          .map((origin) => origin.trim())
          .filter(Boolean) ?? [],
    ),
  REQUIRE_FIREBASE_AUTH: booleanEnvSchema,
  REQUIRE_HTTPS: booleanEnvSchema,
  TRUST_PROXY: booleanEnvSchema,
  PGSSLMODE: z.enum(['disable', 'require']).default('disable'),
});

export type AppEnv = z.infer<typeof envSchema>;

let cachedEnv: AppEnv | null = null;

export function getEnv() {
  if (cachedEnv) {
    return cachedEnv;
  }

  cachedEnv = envSchema.parse(process.env);
  return cachedEnv;
}

export function resetEnvForTests() {
  cachedEnv = null;
}
