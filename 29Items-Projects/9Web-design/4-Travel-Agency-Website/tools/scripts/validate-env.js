const fs = require("node:fs");
const path = require("node:path");

const examplePath = path.join(process.cwd(), ".env.example");
const requiredVariables = [
  "PUBLIC_SITE_URL",
  "PUBLIC_MAP_TILE_URL",
  "DATABASE_URL",
  "DB_POOL_SIZE",
  "CORS_ORIGIN",
  "ENFORCE_HTTPS",
  "CRM_API_KEY",
  "EMAIL_FROM",
  "EMAIL_TO"
];

function main() {
  if (!fs.existsSync(examplePath)) {
    throw new Error(".env.example is missing.");
  }

  const contents = fs.readFileSync(examplePath, "utf8");
  const missing = requiredVariables.filter((name) => !contents.includes(`${name}=`));

  if (missing.length > 0) {
    throw new Error(`.env.example is missing variables: ${missing.join(", ")}`);
  }

  console.log("Environment contract is valid.");
}

main();
