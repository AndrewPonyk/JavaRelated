import { SessionTokenService } from './auth';
import { readRuntimeConfig } from './config';
import { createDatabase } from './database';
import { createApiHandler } from './http';
import { PresetRepository, ProjectRepository, UserRepository } from './repositories';
import { PresetService, ProjectService, UserService } from './services';
import { FixedWindowRateLimiter } from './rate-limit';

export function createApplication(environment: NodeJS.ProcessEnv = process.env) {
  const config = readRuntimeConfig(environment);
  const database = createDatabase(config.DATABASE_URL);
  const users = new UserService(new UserRepository(database));
  const projects = new ProjectService(new ProjectRepository(database));
  const presets = new PresetService(new PresetRepository(database));

  return {
    database,
    config,
    handle: createApiHandler({
      users,
      projects,
      presets,
      tokens: new SessionTokenService(config.JWT_SECRET),
      health: async () => {
        await database.query('SELECT 1');
      },
      corsOrigin: config.CORS_ORIGIN,
      rateLimiter: new FixedWindowRateLimiter(config.PRESET_API_RATE_LIMIT_PER_MINUTE),
    }),
  };
}
