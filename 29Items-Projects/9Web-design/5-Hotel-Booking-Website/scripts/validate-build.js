const fs = require("node:fs");
const path = require("node:path");

const requiredFiles = [
  "src/index.html",
  "src/assets/css/styles.css",
  "src/assets/js/main.js",
  "src/utils/html.js",
  "netlify/functions/bookings.js",
  "netlify/functions/availability.js",
  "netlify/functions/hotels.js",
  "netlify/functions/rooms.js",
  "netlify/functions/guests.js",
  "netlify/functions/staff-users.js",
  "migrations/001_initial_schema.sql",
  "docker-compose.yml",
  ".env.docker.example",
  "docs/API.md",
  "netlify.toml",
];

const missing = requiredFiles.filter((filePath) => {
  return !fs.existsSync(path.join(process.cwd(), filePath));
});

if (missing.length > 0) {
  console.error(`Missing required build files: ${missing.join(", ")}`);
  process.exit(1);
}

console.warn("Build validation passed.");
