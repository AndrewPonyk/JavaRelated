const { query } = require("../../src/api/db/pool");
const { json } = require("../../src/api/utils/http");

exports.handler = async () => {
  try {
    await query("SELECT 1");
    return json(200, { status: "ok", database: "ok" });
  } catch (_error) {
    return json(503, { status: "error", database: "unavailable" });
  }
};
