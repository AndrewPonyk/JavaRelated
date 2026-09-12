const fs = require("node:fs");
const path = require("node:path");
const { newDb } = require("pg-mem");
const { setDatabaseClient } = require("./pool");

async function configureTestDatabase() {
  const db = newDb({ autoCreateForeignKeyIndices: true });
  db.public.registerFunction({
    name: "now",
    returns: "timestamptz",
    implementation: () => new Date()
  });

  const { Pool } = db.adapters.createPg();
  const pool = new Pool();
  setDatabaseClient(pool);

  const migrationsDirectory = path.join(process.cwd(), "migrations");
  const files = fs
    .readdirSync(migrationsDirectory)
    .filter((file) => file.endsWith(".sql"))
    .sort();

  for (const file of files) {
    const sql = fs.readFileSync(path.join(migrationsDirectory, file), "utf8");
    await pool.query(sql);
  }

  return pool;
}

module.exports = { configureTestDatabase };
