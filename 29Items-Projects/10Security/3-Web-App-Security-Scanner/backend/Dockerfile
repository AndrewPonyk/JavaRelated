# Multi-stage: builder compiles wheels, runtime stays slim + non-root.
FROM python:3.12-slim AS builder

WORKDIR /build
COPY requirements.txt .
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

# ── runtime ────────────────────────────────────────────────────────────
FROM python:3.12-slim

# security headers scanner target needs curl for the healthcheck only
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --shell /usr/sbin/nologin appuser

COPY --from=builder /install /usr/local
WORKDIR /app
COPY app ./app
COPY alembic ./alembic
COPY alembic.ini .
COPY ml ./ml

# Bake the severity model so containers boot with the trained classifier
# (synthetic corpus, deterministic — CI retrains and gates on macro-F1).
RUN python -m ml.train --out ml/severity_model.joblib
RUN chown -R appuser:appuser /app

USER appuser
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD curl -fsS http://localhost:8000/health || exit 1

# Migrations run in a compose-side one-shot service (`migrate`) before the
# API starts — expand/contract policy, TECH-NOTES 3.1.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
