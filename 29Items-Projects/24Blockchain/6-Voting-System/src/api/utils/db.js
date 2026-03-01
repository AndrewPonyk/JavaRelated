const { Pool } = require("pg");
const logger = require("./logger");

const pool = new Pool({
  connectionString: process.env.DATABASE_URL || "postgresql://postgres:postgres@localhost:5432/voting_system",
  max: 20,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 2000,
});

pool.on("error", (err) => {
  logger.error("Unexpected PostgreSQL pool error:", err);
});

pool.on("connect", () => {
  logger.debug("New PostgreSQL client connected");
});

/**
 * Execute a SQL query.
 * @param {string} text - SQL query string
 * @param {Array} params - Query parameters
 * @returns {import("pg").QueryResult}
 */
async function query(text, params) {
  const start = Date.now();
  const result = await pool.query(text, params);
  const duration = Date.now() - start;

  if (duration > 500) {
    logger.warn(`Slow query (${duration}ms): ${text}`);
  }

  return result;
}

module.exports = { query, pool };
