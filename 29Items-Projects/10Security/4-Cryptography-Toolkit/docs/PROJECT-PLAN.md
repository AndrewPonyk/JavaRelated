# Cryptography Toolkit — Project Plan

Educational platform for cryptography: interactive demonstrations of AES, RSA,
ECDSA, SHA-3, Argon2 password hashing, and a TLS 1.3 protocol walkthrough with
attack/countermeasure illustrations.

- **Stack:** Python 3.12 · Flask · PyCryptodome · OpenSSL · React 18 (Vite) · SQLite (dev) / PostgreSQL (prod)
- **CI/CD:** GitHub Actions
- **Repo root:** `4-Cryptography-Toolkit/`

---

## 1.1 Project File Structure

```
4-Cryptography-Toolkit/
├── docs/                          # Architecture & tech notes (this folder)
│   ├── PROJECT-PLAN.md
│   ├── ARCHITECTURE.md
│   └── TECH-NOTES.md
│
├── backend/                       # Flask API + crypto core (Python)
│   ├── crypto_toolkit/
│   │   ├── app.py                 # App factory, blueprint registration
│   │   ├── config.py              # Dev/Staging/Prod config classes
│   │   ├── extensions.py          # SQLAlchemy, rate-limiter singletons
│   │   ├── api/
│   │   │   ├── routes_crypto.py   # /api/aes, /api/rsa, /api/ecdsa, /api/sha3, /api/argon2
│   │   │   ├── routes_tls.py      # /api/tls13/* — handshake demo, downgrade sim
│   │   │   └── routes_lessons.py  # CRUD for educational lessons
│   │   ├── services/              # All crypto logic lives here (framework-free)
│   │   │   ├── aes_service.py     # AES-GCM/CBC encrypt+decrypt, visual rounds
│   │   │   ├── rsa_service.py     # Keygen, encrypt/decrypt, sign/verify, OAEP
│   │   │   ├── ecdsa_service.py   # P-256 keygen, sign, verify, k-reuse demo
│   │   │   ├── sha3_service.py    # SHA3-256/384/512, Keccak visual steps
│   │   │   ├── argon2_service.py  # Password hashing + parameter tuning
│   │   │   └── tls_demo_service.py# TLS 1.3 handshake state machine sim
│   │   ├── models/                # SQLAlchemy ORM models
│   │   │   └── lesson.py
│   │   └── middleware/
│   │       └── error_handlers.py  # Uniform JSON error envelope
│   ├── tests/                     # Pytest: unit (services) + integration (API)
│   ├── requirements.txt
│   ├── requirements-dev.txt
│   ├── pyproject.toml             # Ruff, Black, pytest, coverage config
│   ├── Dockerfile
│   └── gunicorn.conf.py
│
├── frontend/                      # React SPA (Vite)
│   ├── src/
│   │   ├── api/client.js          # Fetch wrapper, error normalization
│   │   ├── components/
│   │   │   ├── CryptoLab.jsx      # Data-fetching demo (loading/error/success)
│   │   │   └── StatusBadge.jsx
│   │   ├── App.jsx
│   │   ├── main.jsx
│   │   └── styles.css
│   ├── index.html
│   ├── package.json
│   ├── vite.config.js             # Dev proxy → Flask :5000
│   ├── Dockerfile                 # Multi-stage build → nginx
│   └── nginx.conf                 # SPA fallback + reverse proxy /api
│
├── migrations/
│   └── 001_init.sql               # Initial schema (lessons, users, audit_log)
│
├── nginx/tls/
│   └── openssl.cnf                # SAN + TLS 1.3 cert profile for local demo
│
├── scripts/
│   ├── gen_dev_certs.sh           # Generates local CA + leaf certs via OpenSSL
│   └── smoke_test.sh              # Post-deploy endpoint check
│
├── .github/workflows/
│   ├── ci.yml                     # lint → test → build (both apps)
│   └── deploy.yml                 # Image push + deploy on tags
│
├── docker-compose.yml             # backend + frontend + nginx(TLS) for local demo
├── .env.example
├── .gitignore
├── .pre-commit-config.yaml
└── README.md
```

**Rationale**
- `services/` is deliberately framework-free (pure functions over bytes/dicts) so
  crypto logic is unit-testable without Flask and reusable from a future CLI.
- API layer is thin: validate → delegate to service → shape response.
- Frontend is a standard Vite SPA; nginx terminates TLS 1.3 so the browser-side
  TLS demo works against a real endpoint, not a simulation only.

---

## 1.2 Implementation TODO List

### Phase 1 — Foundation (high priority)
- [x] Scaffold repo, configs, linters, pre-commit, CI skeleton (lint+test gates)
- [x] Flask app factory + config classes + health endpoint `/api/health`
- [x] Implement `aes_service` (GCM + CBC, random IVs, tag verification)
- [x] Implement `sha3_service` (SHA3-256/384/512 via PyCryptodome)
- [x] Implement `argon2_service` (hash/verify, parameter presets: interactive/moderate/paranoid)
- [x] `/api/aes`, `/api/sha3`, `/api/argon2` routes + input validation (marshmallow)
- [x] DB: `001_init.sql` + SQLAlchemy `Lesson` model + lessons CRUD
- [x] React shell: routing, api client, `CryptoLab` demo wired to AES endpoint
- [x] docker-compose with HTTPS nginx front (local TLS 1.3 works end-to-end)

### Phase 2 — Core features (medium priority) — complete
- [x] `rsa_service`: keygen, OAEP encrypt/decrypt, PSS sign/verify; textbook-vs-OAEP demo
- [x] `ecdsa_service`: P-256 keygen/sign/verify; deterministic vs random-k; **k-reuse key-recovery demo**
- [x] `tls_demo_service`: TLS 1.3 handshake step-through (ClientHello → key share → finished), rendered stepwise in UI
- [x] Attack gallery: ECB penguin, hash-length-extension (SHA-256 vs SHA-3), downgrade strip
- [x] Countermeasure notes attached to each attack (educational pairing)
- [x] Rate limiting on all crypto endpoints (CPU-bound protection)
- [x] Audit log table (who ran which demo, when — no plaintext inputs)
- [x] Vitest + React Testing Library for CryptoLab; API integration tests for every route
- [x] (added during implementation) Auth module: Argon2id accounts, signed tokens,
      first-user-is-admin bootstrap, TOTP 2FA (RFC 6238, Fernet-encrypted secret),
      admin-gated lesson writes + audit access (`docs/ARCHITECTURE.md §2.5`)
- [x] (added during implementation) Seed lessons for every topic; markdown lesson UI with admin editing

### Phase 3 — Polish & optimization (lower priority)
- [x] Coverage ≥ 85% backend (91% at 119 tests) — mutation testing (mutmut) still open
- [ ] Playwright E2E happy-path suite (run AES demo, run TLS walkthrough)
- [x] GitHub Actions deploy workflow: build images → GHCR → SSH rollout to staging/production (tag) with smoke gates
- [ ] Password-hashing parameter benchmark page (Argon2 ms/iteration chart)
- [ ] i18n pass (EN first), accessibility audit — [x] dark mode (`prefers-color-scheme` tokens)
- [x] OpenAPI spec + docs page — hand-maintained spec at `/api/openapi.json` +
      `/api/docs` viewer (deliberate deviation: single source of truth without
      the flask-smorest codegen dependency; a test asserts every route is in the spec)
- [x] Production hardening: CSP/HSTS/X-Frame-Options headers + HTTP→HTTPS redirect
      at the edge (`nginx/tls/edge.conf`), secrets via env / GH Actions secrets,
      `ProductionConfig` refuses to boot without a real `SECRET_KEY`
- [x] Production audit pass (this phase): migration↔ORM drift fixed and guarded by
      `tests/test_schema_parity.py` (prod applies SQL first; `create_all` skips
      existing tables, so drift only manifested deployed); lessons list paginates
      (`limit`/`offset`, clamped 1–100); TOTP replay protection per RFC 6238 §5.2
      (`users.totp_last_timestep`); ruff+black+prettier all clean and CI-gated;
      `datetime.utcnow` deprecation removed (naive-UTC `models/clock.py`);
      markdown renderer escapes all five HTML entities; edge gzip for static only
      (JSON uncompressed — BREACH); deps upgraded to zero pip-audit findings
      (flask 3.1.3, marshmallow 3.26.2, cryptography 50.0.0) and zero npm-audit
      findings (vite 8, vitest 5, react-router-dom 7); README troubleshooting
      section. Remaining known-open: Playwright E2E, Argon2 benchmark page, i18n.
