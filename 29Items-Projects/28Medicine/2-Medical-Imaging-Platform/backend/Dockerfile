# syntax=docker/dockerfile:1.7
# ── Stage 1: builder ─────────────────────────────────────────
# Pin base images by digest in real builds (omitted here for readability).
FROM python:3.12-slim AS builder

ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PYTHONDONTWRITEBYTECODE=1

WORKDIR /build
# No compiler needed: every dependency (numpy, pydicom, pylibjpeg*, asyncpg,
# cryptography, bcrypt, pydantic-core) ships a manylinux wheel.
COPY requirements.txt .
RUN python -m venv /opt/venv \
    && /opt/venv/bin/pip install -r requirements.txt

# ── Stage 2: runtime ─────────────────────────────────────────
FROM python:3.12-slim AS runtime

ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    APP_ENV=production

# Non-root user (HIPAA least-privilege; never run as root)
RUN groupadd --system app && useradd --system --gid app --no-create-home appuser

WORKDIR /app
COPY --from=builder /opt/venv /opt/venv
COPY app ./app
COPY migrations ./migrations
COPY alembic.ini ./alembic.ini
COPY entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh

USER appuser
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
    CMD python -c "import httpx,sys; sys.exit(0 if httpx.get('http://localhost:8000/api/v1/health/live').status_code==200 else 1)"

# Entrypoint waits for the DB (+ migrates when RUN_MIGRATIONS=true), then runs CMD.
# Workers run the same image with a different command (see docker-compose).
ENTRYPOINT ["./entrypoint.sh"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
