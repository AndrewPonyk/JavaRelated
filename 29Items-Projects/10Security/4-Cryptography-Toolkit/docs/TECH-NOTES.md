# Cryptography Toolkit — Technical Notes

## 3.1 CI/CD Pipeline Design (`.github/workflows/ci.yml`)

```
lint → test → build
```

1. **lint (fast, ~30s):** `ruff check` + `black --check` (backend);
   `eslint` + `prettier --check` (frontend). Fail fast before tests burn minutes.
2. **test:** `pytest --cov=crypto_toolkit --cov-fail-under=85` with a
   `services`-first posture (unit tests need no Flask context); `vitest run`
   for the SPA. Matrix: Python 3.12 only (crypto libs — don't matrix what you
   can't support).
3. **build:** docker build both images (proves Dockerfiles green);
   `pip-audit` / `npm audit --audit-level=high` gates.
4. **deploy (`.github/workflows/deploy.yml`)** — on `v*` tags only:
   push images to GHCR → deploy staging → `scripts/smoke_test.sh` → manual
   `environment: production` approval gate → deploy prod.

Concurrency group per branch + `cancel-in-progress` so PR pushes don't queue.

## 3.2 Testing Strategy

- **Unit (backend, pytest):** the `services/` layer is pure — feed bytes, assert
  bytes. Round-trip property for every cipher (`decrypt(encrypt(x)) == x`),
  tamper tests (flip one ciphertext byte → `VerificationError`), RFC/NIST test
  vectors for AES-GCM/SHA-3 where public. Target: **≥ 85% lines on
  `crypto_toolkit`, 100% on `services/`.**
- **Integration (backend, pytest + Flask test client):** each route gets
  happy-path + validation-failure (422 envelope shape) + rate-limit tests;
  `conftest.py` provides `app`/`client` fixtures with in-memory SQLite.
- **Frontend (Vitest + React Testing Library):** `CryptoLab` rendered with a
  mocked fetch — loading skeleton, error banner, success rendering. User-centric
  queries (`findByRole`), no implementation-detail tests.
- **E2E (Playwright, Phase 3):** one golden path per demo (AES round-trip,
  TLS walkthrough completes) against docker-compose. E2E is a smoke net, not
  the primary suite.

## 3.3 Deployment Strategy

- **Containers everywhere:** `backend/Dockerfile` (python:3.12-slim, non-root
  user, gunicorn) and `frontend/Dockerfile` (node build stage → nginx:alpine
  serving static + proxying `/api`). One `docker-compose.yml` runs the whole
  stack locally with real TLS 1.3 via `nginx/tls/`.
- **Target infra:** any Docker host (single VM, ECS, Cloud Run — the images are
  identical). The deploy workflow tags images `sha` + `vX.Y.Z` and rolls the
  backend with a health-check gated swap (`/api/health` before traffic shifts).
- **Migrations run as a deploy pre-step**, not at app boot: `001_init.sql` is
  idempotent (`CREATE TABLE IF NOT EXISTS`) and forward-only for v1.

## 3.4 Environment Management

- All config from env; `config.py` maps `FLASK_ENV=development|testing|
  staging|production` to classes that override `BaseConfig`. No config
  branches outside that file.
- `.env` is git-ignored; `.env.example` is the source of truth for keys.
- CI uses repo secrets; docker-compose injects via `env_file`.
- Rule: **defaults are safe** — e.g., a missing `RATELIMIT` falls back to the
  strictest value, not the loosest.

`.env.example` template (root of repo):

```dotenv
# --- Flask ---
FLASK_ENV=development            # development | testing | staging | production
SECRET_KEY=change-me-generate-with-openssl-rand-hex-32
DATABASE_URL=sqlite:///crypto_toolkit.db

# --- API behaviour ---
RATELIMIT_DEFAULT=30 per minute
MAX_PAYLOAD_KB=64

# --- TLS demo (paths mounted into nginx) ---
TLS_CERT_PATH=/etc/nginx/tls/server.crt
TLS_KEY_PATH=/etc/nginx/tls/server.key

# --- Frontend (build-time) ---
VITE_API_BASE_URL=/api
```

## 3.5 Version Control Workflow

**Trunk-based with short-lived branches** (GitHub Flow + release tags):

- `master` is always deployable; feature branches (`feat/ecdsa-k-reuse`,
  `fix/gcm-tag-422`) live < 2 days, squash-merged via PR with required CI.
- Releases: tag `vX.Y.Z` on `master` triggers `deploy.yml`.
- Why not Gitflow: this is not a scheduled-release product; the two long-lived
  envs (staging, prod) map cleanly to *tag → staging → promote*, and Gitflow's
  `develop`/`release` ceremony would double PR overhead for a small team.
- Commits: Conventional Commits (`feat:`, `fix:`, `docs:`) — keeps the tag
  changelog generatable.

## 3.6 Common Pitfalls (this stack specifically)

1. **PyCryptodome vs the `pycrypto` name collision** — installing `pycrypto`
   (dead, CVE-ridden) silently shadows things. Pin `pycryptodome==3.*` in both
   requirements files; never `pip install pycrypto`.
2. **ECB "works" and students ship it** — PyCryptodome happily does ECB. The
   service layer must refuse ECB except through the explicit attack-demo path
   (`demo=true`), and CBC must generate a fresh random IV per call.
3. **Nonce reuse in GCM** — catastrophic (auth key recovery). Never derive the
   IV from anything but `os.urandom(12)` per encryption; add a regression test
   that two identical plaintexts produce different IVs.
4. **RSA textbook pitfalls** — signing raw hashes without PSS, PKCS#1 v1.5
   encryption padding oracles. Expose only OAEP/PSS; textbook modes stay
   inside the attack gallery with a warning banner.
5. **ECDSA `k` reuse** — the classic Sony leak. It's *the* demo we want, which
   means the recovery code is intentionally "insecure math in a sandbox";
   keep it clearly quarantined and documented so it's never copy-pasted as
   production code.
6. **Argon2 defaults drift** — `low` preset on a beefy server feels instant and
   teaches nothing; benchmark and pin explicit `(time_cost, memory_cost,
   parallelism)` triples per preset, don't trust library defaults.
7. **TLS demo confusion: simulation vs reality** — the handshake walkthrough is
   a *simulation* rendered from `tls_demo_service`; students may believe it's
   real. Always pair it with "inspect the actual connection" (nginx really is
   TLS 1.3), and label the sim clearly in the UI.
8. **Windows dev quirks** — `os.urandom` is fine, but paths in
   `scripts/gen_dev_certs.sh` assume bash; document WSL/Git-Bash for cert
   generation, and keep `/dev/null`-style shellisms out of Python code.
9. **CPU exhaustion via the demos** — keygen + Argon2 are DoS vectors on an
   open endpoint; rate-limit *before* doing crypto, and cap payload sizes in
   the nginx layer as well as the app.
10. **React state holding secrets** — demo plaintexts in React state end up in
    Redux devtools/screenshots; keep demo inputs ephemeral, offer a "clear"
    action, and never persist them to `localStorage`.
