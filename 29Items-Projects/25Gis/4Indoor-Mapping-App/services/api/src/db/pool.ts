import { Pool } from 'pg';

import { getEnv } from '../config/env';

const env = getEnv();

export const pool = new Pool({
  connectionString: env.DATABASE_URL,
  max: 10,
  idleTimeoutMillis: 30_000,
  ssl: env.PGSSLMODE === 'require' ? { rejectUnauthorized: true } : undefined,
});

export type Database = Pick<Pool, 'query'>;
