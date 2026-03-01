/**
 * Test setup — creates tables in test database and provides helpers.
 */
const { pool } = require("../../src/api/utils/db");
const fs = require("fs");
const path = require("path");

async function setupTestDb() {
  const sqlDir = path.join(__dirname, "../../migrations/sql");
  const files = fs.readdirSync(sqlDir).filter((f) => f.endsWith(".sql")).sort();

  for (const file of files) {
    const sql = fs.readFileSync(path.join(sqlDir, file), "utf8");
    await pool.query(sql);
  }
}

async function cleanTestDb() {
  await pool.query("DELETE FROM delegation_events");
  await pool.query("DELETE FROM vote_reveals");
  await pool.query("DELETE FROM vote_commits");
  await pool.query("DELETE FROM amendments");
  await pool.query("DELETE FROM proposal_options");
  await pool.query("DELETE FROM proposals");
  await pool.query("DELETE FROM auth_nonces");
  await pool.query("DELETE FROM users");
  await pool.query("DELETE FROM sync_state");
}

async function seedTestData() {
  // Create a test user
  await pool.query(
    "INSERT INTO users (wallet_address, display_name) VALUES ($1, $2) ON CONFLICT DO NOTHING",
    ["0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266", "Test User"]
  );

  // Create a test proposal
  await pool.query(
    `INSERT INTO proposals (id, title, description_cid, proposer_address, phase, option_count, total_votes)
     VALUES (1, 'Test Proposal', 'QmTestCid', '0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266', 'draft', 2, 0)
     ON CONFLICT DO NOTHING`
  );

  // Create options
  await pool.query(
    "INSERT INTO proposal_options (proposal_id, option_index, label) VALUES (1, 0, 'Yes'), (1, 1, 'No') ON CONFLICT DO NOTHING"
  );

  // Reset sequence
  await pool.query("SELECT setval('proposals_id_seq', (SELECT COALESCE(MAX(id), 0) FROM proposals))");
}

async function closeDb() {
  await pool.end();
}

module.exports = { setupTestDb, cleanTestDb, seedTestData, closeDb, pool };
