import 'dotenv/config';
import { pathToFileURL } from 'node:url';
import { getPool } from '../db/connection';
import { runMigrations } from '../db/migrate';
import { seedDatabase } from '../db/seed';
import { PgTokenRepository } from '../db/token.repository';
import { TokenService } from '../services/token.service';
import { createApp } from './app';

export async function startServer(): Promise<void> {
  if (process.env.AUTO_MIGRATE === 'true') {
    await runMigrations();
    await seedDatabase();
  }

  const port = Number(process.env.PORT ?? 4100);
  const tokenService = new TokenService(new PgTokenRepository(getPool()));
  const app = createApp(tokenService);

  app.listen(port, () => {
    console.warn(`Design system API listening on http://localhost:${port}`);
  });
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  startServer().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
