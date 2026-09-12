import pg from 'pg';

let pool;

export function getPool() {
  if (pool) return pool;

  const connectionString = process.env.DATABASE_URL;

  if (!connectionString) {
    throw new Error('DATABASE_URL is required for reservation database access.');
  }

  pool = new pg.Pool({
    connectionString,
    ssl: shouldUseSsl(connectionString) ? { rejectUnauthorized: false } : false,
    max: Number(process.env.DB_POOL_SIZE || 10)
  });

  return pool;
}

export async function closePool() {
  if (pool) {
    await pool.end();
    pool = undefined;
  }
}

function shouldUseSsl(connectionString) {
  return process.env.DB_SSL === 'true' || /sslmode=require/.test(connectionString);
}
