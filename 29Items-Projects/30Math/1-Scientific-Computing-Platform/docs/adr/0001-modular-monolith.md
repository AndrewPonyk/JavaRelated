# ADR-0001: Modular monolith + async compute plane instead of microservices

**Status:** Accepted · **Date:** 2026-07-08

## Context

The platform serves interactive education workloads: symbolic/numerical
computation (CPU-bound, occasionally unbounded), notebook-based teaching, and
an ML classification model. Team is small; traffic is classroom-scale and
bursty. The dominant risks are (a) untrusted mathematical input executing in
our processes and (b) operational overload from too many deployables.

## Decision

Build a **modular monolith** (FastAPI) with strict internal layering, a
**separate worker deployment** (Celery + Redis) running the *same codebase*
for heavy jobs, and keep ML training/serving on **AWS SageMaker** behind a
single `InvokeEndpoint` seam. The scientific kernel is a standalone library
(`libs/sciengine`) with no framework dependencies.

## Consequences

- ✅ One lockfile, one API deployable, trivial local dev (`docker compose up`).
- ✅ Process isolation exists exactly where needed (worker subprocesses), not
  everywhere by default.
- ✅ Notebooks, API, workers, and ML featurization share one math
  implementation — no drift.
- ✅ Module seams (`services/*`) make later extraction mechanical if scale
  demands it (compute plane is already physically separate).
- ⚠️ Single database schema shared by API and workers — migrations must stay
  backward-compatible one release back.
- ⚠️ A defect in shared code ships to API and workers simultaneously —
  mitigated by the test pyramid weighted toward `sciengine`.

## Alternatives considered

- **Microservices (solver/plotter/auth/ml as services)** — rejected: network
  contracts between math modules that naturally share dataclasses; 4–6×
  deployment surface for zero scale benefit at classroom volume.
- **Serverless-only (Lambda)** — rejected: SymPy worst cases exceed
  comfortable duration/memory envelopes; cold starts hurt interactive UX;
  container reuse benefits (warm BLAS, font caches) are lost.
