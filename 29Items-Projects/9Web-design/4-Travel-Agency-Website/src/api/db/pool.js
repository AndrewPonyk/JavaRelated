const { Pool } = require("pg");

let pool;
let overrideClient;

function createPool() {
  const connectionString = process.env.DATABASE_URL;

  if (!connectionString) {
    throw new Error("DATABASE_URL is required for database access.");
  }

  return new Pool({
    connectionString,
    ssl: process.env.PGSSLMODE === "require" ? { rejectUnauthorized: false } : undefined,
    max: Number(process.env.DB_POOL_SIZE || 5)
  });
}

function getClient() {
  if (overrideClient) {
    return overrideClient;
  }

  if (!pool) {
    pool = createPool();
  }

  return pool;
}

async function query(text, params) {
  return getClient().query(text, params);
}

function setDatabaseClient(client) {
  overrideClient = client;
}

async function closeDatabase() {
  if (pool) {
    await pool.end();
    pool = undefined;
  }
}

module.exports = { closeDatabase, getClient, query, setDatabaseClient };
