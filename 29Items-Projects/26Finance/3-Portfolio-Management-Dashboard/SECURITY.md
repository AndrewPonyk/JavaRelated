# Security Posture

## Controls in place
- **Auth:** OAuth2 password flow → JWT (HS256, PyJWT with an explicit algorithm
  allow-list on decode). Passwords hashed with **bcrypt** (`passlib`).
- **Authorization:** every portfolio/holding/job query is scoped to the owner;
  non-owners get `404` (existence is not leaked).
- **Input validation:** Pydantic v2 on all request bodies + `Query` bounds on
  params. ORM/parameterized queries throughout (no string-built SQL).
- **Transport / headers:** TLS terminated at the ALB; the API sets
  `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, and (in prod)
  `Strict-Transport-Security`.
- **Secrets:** never in code or images — local `.env` (git-ignored) and AWS
  Secrets Manager in ECS (see `infrastructure/ecs/task-definition.json`).
- **Rate limiting:** per-IP sliding window middleware.
- **Dependency scanning:** `pip-audit` (backend) and `npm audit` (frontend) run
  in CI.

## Dependency audit status

Fixed in this hardening pass:
- Removed **python-jose** (algorithm-confusion / JWT-bomb CVEs) → migrated to **PyJWT 2.13.0**.
- Bumped **python-multipart 0.0.20 → 0.0.32** (clears CVE-2026-24486 and others).
- Bumped **FastAPI 0.115.6 → 0.116.1**, which pulls **starlette ≥ 0.47.2**
  (clears CVE-2025-54121, the multipart event-loop DoS).

### Tracked (not yet applied)
`pip-audit` still reports transitive **starlette** advisories that require a
**starlette 1.x** major upgrade (`CVE-2025-62727` → 0.49.1; `CVE-2026-48817/48818`,
`PYSEC-2026-161/248/249` → 1.x):

- **Remediation:** a coordinated FastAPI + Starlette major upgrade, validated by
  the full test suite (Starlette 1.x has breaking changes), tracked as a
  dedicated change rather than a blind pin.
- **Risk assessment for this app: low.** The reported vectors are multipart /
  large-upload parsing DoS. This service exposes **no file-upload endpoints** —
  the only multipart payload is the small OAuth2 login form — so the vectors are
  not reachably exploitable here.

## Reporting
Report suspected vulnerabilities privately to the maintainers rather than via a
public issue.
