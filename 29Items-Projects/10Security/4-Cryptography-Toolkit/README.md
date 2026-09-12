# Cryptography Toolkit

Educational platform for cryptography: interactive demonstrations of
**AES, RSA, ECDSA, SHA-3, Argon2** password hashing, and a **TLS 1.3**
handshake walkthrough — each paired with attack illustrations and the
countermeasures that defeat them.

## What's inside

### Crypto labs (live, real cryptography — not simulations)

1. **AES encrypt/decrypt** — AES-GCM by default (authenticated, returns ciphertext + IV + tag); CBC and ECB modes available demo-only; keys 128/192/256-bit, base64 in/out.
2. **SHA-3 hashing** — SHA3-256/384/512 digests, verified against FIPS 202 test vectors.
3. **Argon2id password hashing** — hash + verify with pinned presets (interactive vs. paranoid cost), the modern answer to GPU cracking.
4. **RSA keygen + encrypt/decrypt** — 2048–4096-bit keys, RSA-OAEP with SHA-256 (rejects undersized keys and too-long messages with a hybrid-encryption hint).
5. **RSA sign/verify** — RSA-PSS with SHA-256 and 32-byte salt.
6. **ECDSA keygen/sign/verify** — P-256/P-384, with a determinism demo: random-k (new signature every run) vs. RFC 6979 deterministic nonces (same signature every run).
7. **TLS 1.3 handshake walkthrough** — runs a *real* key schedule: X25519 exchange, full RFC 8446 §7.1 HKDF ladder, transcript hashes, both Finished MACs, per-record nonce = static_iv ⊕ seq, a real AES-GCM application record, and a PSK resumption ticket — every intermediate value shown step-by-step; both SHA256 and SHA384 suites.
8. **Single-step HKDF-Expand-Label** — feed a secret + label, get the TLS 1.3-derived output.
9. **Downgrade-attack simulation** — how version-stripping MITM worked against TLS ≤1.2 and the three defences that kill it (DOWNGRD sentinel, encrypted handshake, HSTS/TLS-1.3-only edge).

### Attack gallery (each break paired with its countermeasure)

10. **ECB penguin** — see block-pattern leakage survive ECB and die under CBC.
11. **SHA-256 length extension** — a *real* forgery: resume the hash midstate without the key, forge `&admin=true`, watch the naive `SHA256(key‖msg)` server accept it while HMAC and SHA-3 refuse.
12. **GCM nonce reuse** — two ciphertexts under one nonce; XOR cancels the keystream and recovers a secret plaintext you never had the key for.
13. **Textbook-RSA malleability** — multiply a ciphertext by E(2) using only the public key; decryption yields 2m.
14. **ECDSA k-reuse** — the Sony leak, live: sign two messages with one nonce and recover the private key.

### Lessons & content

15. **Six seeded lessons** (one per topic: AES, RSA, ECDSA, SHA-3, Argon2, TLS), each linked to its live demo endpoint; filter by topic, paginated listing, markdown rendering.
16. **Admin lesson CMS** — create/edit/delete lessons through the UI or API (admin-gated).

### Accounts & security operations

17. **Register/login** — Argon2id-hashed passwords, timing-safe login, first registered user becomes admin (documented bootstrap).
18. **TOTP 2FA** (RFC 6238) — enrol with any authenticator app (otpauth URI shown), enable/disable, replay-protected logins (a code never works twice).
19. **Bearer-token auth** — 12-hour signed tokens, no cookies (CSRF-immune by design).
20. **Admin audit log** — every demo operation recorded with scrubbed parameters (deny-list drops anything secret-looking), salted IP hashes, user id, paginated listing.

### Platform

21. **Real TLS 1.3 edge** — nginx serving the app itself over TLS 1.3-only with HSTS, CSP, X-Frame-Options, nosniff, HTTP→HTTPS redirect (students can verify with `openssl s_client`), static-asset gzip only.
22. **Self-documenting API** — interactive docs at `/api/docs`, OpenAPI 3.0 spec at `/api/openapi.json` (33 paths), uniform `{data}` / `{error}` envelope everywhere.
23. **Rate limiting** per endpoint (e.g. Argon2 5/min, attacks 10/min), 64 KB payload cap, marshmallow input validation on every endpoint.
24. **Quality gates** — 119 backend tests (91% coverage) incl. a migration↔ORM schema-parity guard, 47 frontend tests, ruff + black + ESLint + Prettier clean, pip-audit and npm-audit at zero known vulnerabilities.
25. **One-command deploy** — `docker compose up` locally with dev certs; tag-driven GitHub Actions pipeline (build → GHCR → SSH rollout → migrations → health check → smoke test) for staging/production.

## Quick start (docker, real TLS 1.3)

```bash
./scripts/gen_dev_certs.sh        # local CA + SAN leaf cert (bash/Git-Bash/WSL)
docker compose up --build         # edge(443) → frontend → backend
open https://localhost            # accept the dev CA in your browser
```

## Local dev (no docker)

```bash
# backend
cd backend && python -m venv .venv && source .venv/Scripts/activate  # Windows Git-Bash
pip install -r requirements-dev.txt
export FLASK_ENV=development && flask --app crypto_toolkit.app run

# frontend (separate terminal)
cd frontend && npm install && npm run dev   # :5173, proxies /api → :5000
```

## Accounts & roles

Demos are anonymous. Accounts exist only for **lesson editing** and the
**audit log**, and the first registered user becomes the admin:

1. Open the app → *Sign in* → **Register** — the first account gets the
   admin role automatically (documented bootstrap, `auth_service.register`).
2. After registering you may enable **TOTP 2FA** (RFC 6238): scan the
   `otpauth://` URI with any authenticator app and confirm one 6-digit code.
   The secret is stored Fernet-encrypted (key derived from `SECRET_KEY`).
3. Sign in as admin → **Audit** page (or `GET /api/auth/audit`) shows every
   demo operation with scrubbed parameters and salted IP hashes — never
   plaintexts, keys, or passwords (`docs/ARCHITECTURE.md §2.5`).

The demo token is kept in `sessionStorage` (dies with the tab) — never
`localStorage`.

## API

Interactive docs are served by the app at **`/api/docs`**; the raw OpenAPI 3.0
spec is at **`/api/openapi.json`** (33 paths). Highlights:

| Area | Endpoints |
| --- | --- |
| AES | `POST /api/aes/encrypt` · `POST /api/aes/decrypt` (GCM default; CBC/ECB demo-only) |
| RSA | `/api/rsa/keygen` · `/api/rsa/encrypt|decrypt` (OAEP-SHA256) · `/api/rsa/sign|verify` (PSS) · `/api/rsa/attack/malleability` |
| ECDSA | `/api/ecdsa/keygen` · `/api/ecdsa/sign|verify` · `/api/ecdsa/attack/k-reuse` |
| Hashing/KDF | `/api/sha3/digest` · `/api/argon2/hash|verify` · `/api/tls13/hkdf` |
| TLS 1.3 | `/api/tls13/handshake?suite=…` · `/api/tls13/downgrade` |
| Attacks | `/api/attacks/ecb-penguin` · `/api/attacks/length-extension` · `/api/attacks/gcm-nonce-reuse` |
| Lessons | `GET /api/lessons/` (anonymous) · admin-gated `POST/PATCH/DELETE /api/lessons/…` |
| Auth | `/api/auth/register|login|whoami` · `/api/auth/totp/setup|enable|disable` · `/api/auth/audit` (admin) |
| Meta | `/api/health` · `/api/openapi.json` · `/api/docs` |

Every response uses the uniform envelope `{data: …}` or
`{error: {code, message, correlation_id}}`.

## Test

```bash
cd backend && pytest                # 119 tests, 91% coverage (gate 85%)
cd frontend && npm test             # 47 Vitest + React Testing Library tests
BASE=https://localhost ./scripts/smoke_test.sh
```

## Troubleshooting

| Symptom | Cause & fix |
| --- | --- |
| `docker compose` commands hang | Docker Desktop isn't running — start it first (commands block silently until the daemon answers). |
| Edge container exits, `bind() to 0.0.0.0:443 failed` | Another service owns 443/80 (IIS, Skype-legacy, another nginx). Find it: `netstat -ano \| findstr :443`, stop it or remap the edge ports in `docker-compose.yml`. |
| Browser certificate warning on `https://localhost` | Expected — the cert comes from the local dev CA. Accept it, or import `nginx/tls/ca.crt` into your OS/browser trust store for a clean padlock. |
| `gen_dev_certs.sh: openssl: command not found` | The script needs OpenSSL on PATH (Git-Bash ships one; Windows users can add `/usr/bin` or install it). |
| Login works but TOTP codes always rejected | Clock skew: codes are valid for ±30 s only. Sync the machine clock (NTP) and make sure the authenticator app is time-based (TOTP, not HOTP). |
| `Cannot decrypt TOTP secret (SECRET_KEY changed?)` | The TOTP secret is Fernet-encrypted under `SECRET_KEY`. Rotating the key invalidates enrolments (and login tokens) — disable/re-enable TOTP after a rotation. Keep the key stable otherwise. |
| Backend refuses to boot in production (`SECRET_KEY must be set`) | Deliberate `ProductionConfig` guard — set a real `SECRET_KEY` in the host `.env` (see `.env.example`). |
| Lost the admin account | The **first** registered user becomes admin. In dev, deleting the sqlite file re-bootstraps; in production register immediately after the very first deploy. |
| 429 responses while testing the API by hand | Per-endpoint rate limits (e.g. Argon2 5/min, attacks 10/min) are doing their job — wait a minute or adjust `RATELIMIT_DEFAULT` in the environment. |

## Deploy (tag-driven)

`git tag v1.2.3 && git push --tags` → `.github/workflows/deploy.yml` builds
and pushes GHCR images, then SSH-rollouts `docker compose` to **staging** and
(after manual approval) **production**, each gated by the health check and
smoke test. One-time host setup and the required repository secrets
(`STAGING_HOST`, `PRODUCTION_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`,
`DEPLOY_PATH`, `STAGING_URL`, `PRODUCTION_URL`) are documented in the header
of [`docker-compose.deploy.yml`](docker-compose.deploy.yml).

## Layout

- `docs/` — project plan, architecture, tech notes
- `backend/crypto_toolkit/services/` — all crypto logic (framework-free)
- `backend/crypto_toolkit/api/` — thin Flask blueprints (validate → service → shape)
- `frontend/src/` — React SPA (Vite)
- `nginx/tls/` — edge config: the *real* TLS 1.3 endpoint used by the lesson
- `migrations/` — idempotent SQL schema (`scripts/run_migrations.py`)
- `.github/workflows/` — CI (lint→test→build) and tag-driven deploy

Security posture: demo plaintexts/keys are ephemeral and never logged or
persisted; insecure modes (ECB, textbook RSA) exist only inside the clearly
labelled attack gallery. See `docs/ARCHITECTURE.md §2.5`.
