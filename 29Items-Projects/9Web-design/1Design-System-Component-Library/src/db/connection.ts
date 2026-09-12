import 'dotenv/config';
import pg from 'pg';

const { Pool } = pg;

let sharedPool: pg.Pool | undefined;

export function getDatabaseUrl(): string {
  return (
    process.env.DATABASE_URL ??
    'postgres://design_system:design_system@localhost:5432/design_system'
  );
}

export function getPool(): pg.Pool {
  if (!sharedPool) {
    sharedPool = new Pool({
      connectionString: getDatabaseUrl()
    });
  }

  return sharedPool;
}

export async function closePool(): Promise<void> {
  if (sharedPool) {
    await sharedPool.end();
    sharedPool = undefined;
  }
}
