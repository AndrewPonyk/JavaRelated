#!/usr/bin/env node
/**
 * Fails fast when a build is about to run with missing/nonsensical configuration.
 * Used by CI before `vite build` (see .github/workflows/ci.yml). Locally, Vite's
 * .env files make this mostly a no-op — it exists to catch CI misconfiguration.
 */
const REQUIRED = ['VITE_ENV_NAME', 'VITE_API_BASE_URL'];
const KNOWN_ENVS = ['local', 'e2e', 'preview', 'staging', 'production'];

const missing = REQUIRED.filter((name) => !process.env[name]);
const errors = [];

if (missing.length > 0) {
  errors.push(`Missing required build variables: ${missing.join(', ')}`);
}

const envName = process.env.VITE_ENV_NAME;
if (envName && !KNOWN_ENVS.includes(envName)) {
  errors.push(`VITE_ENV_NAME="${envName}" is not one of: ${KNOWN_ENVS.join(', ')}`);
}

// Guard rail: production builds must not ship the mock backend.
if (envName === 'production' && process.env.VITE_ENABLE_MSW === 'true') {
  errors.push('VITE_ENABLE_MSW=true is forbidden for production builds.');
}

// Heuristic secret detection — VITE_* values are public (docs/TECH-NOTES.md §3.4).
for (const [key, value] of Object.entries(process.env)) {
  if (key.startsWith('VITE_') && value && /(secret|password|private[_-]?key)/i.test(key)) {
    errors.push(`Suspicious variable "${key}": VITE_* values are embedded in the public bundle.`);
  }
}

if (errors.length > 0) {
  for (const err of errors) console.error(`✖ ${err}`);
  process.exit(1);
}

console.log(`✔ Build environment OK (VITE_ENV_NAME=${envName})`);
