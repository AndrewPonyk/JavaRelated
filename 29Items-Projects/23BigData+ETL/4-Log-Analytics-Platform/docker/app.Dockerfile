# One image for all three Python services — the CMD picks the role:
#   api      → (default) uvicorn log_analytics.api.main:app
#   gateway  → uvicorn log_analytics.ingestion.gateway:app --port 8080
#   alerting → python -m log_analytics.alerting.engine
# Keeps ECR simple: one build, three ECS task definitions with command overrides.

FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Dependency layer first — cache survives source changes.
COPY requirements.txt ./
RUN pip install -r requirements.txt

COPY src/ src/
COPY config/ config/
COPY frontend/ frontend/
# Operational tooling baked in: the compose/ECS `init` one-shot runs migrations + topics.
COPY scripts/ scripts/
COPY elasticsearch/ elasticsearch/

ENV PYTHONPATH=/app/src

# Non-root: log pipelines are a favorite lateral-movement target.
RUN useradd --system --uid 10001 appuser
USER appuser

EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s CMD ["python", "-c", \
  "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/healthz', timeout=2).status==200 else 1)"]

CMD ["uvicorn", "log_analytics.api.main:app", "--host", "0.0.0.0", "--port", "8000"]
