const fs = require("node:fs");
const path = require("node:path");
const { randomUUID } = require("node:crypto");
const { DataType, newDb } = require("pg-mem");
const appDb = require("../../netlify/functions/_lib/db");

async function createTestDatabase() {
  const memory = newDb({ autoCreateForeignKeyIndices: true });
  memory.public.registerFunction({
    name: "gen_random_uuid",
    returns: DataType.uuid,
    impure: true,
    implementation: randomUUID,
  });

  const migrationPath = path.join(__dirname, "../../migrations/001_initial_schema.sql");
  const migration = fs
    .readFileSync(migrationPath, "utf8")
    .replace(/CREATE EXTENSION IF NOT EXISTS pgcrypto;\s*/i, "");

  memory.public.none(migration);

  const adapter = memory.adapters.createPg();
  const pool = new adapter.Pool();
  appDb.setPool(pool);

  return {
    pool,
    query: (text, params) => pool.query(text, params),
    withTransaction: async (callback) => {
      const client = await pool.connect();

      try {
        await client.query("BEGIN");
        const result = await callback(client);
        await client.query("COMMIT");
        return result;
      } catch (error) {
        await client.query("ROLLBACK");
        throw error;
      } finally {
        client.release();
      }
    },
    close: async () => {
      await pool.end();
      appDb.setPool(undefined);
    },
  };
}

module.exports = {
  createTestDatabase,
};
