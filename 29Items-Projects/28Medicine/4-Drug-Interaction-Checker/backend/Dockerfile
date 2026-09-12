# syntax=docker/dockerfile:1
FROM python:3.12-slim AS base
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1
WORKDIR /app

FROM base AS deps
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

FROM deps AS runtime
COPY app ./app
# Run as a non-root user.
RUN adduser --disabled-password --gecos "" appuser && chown -R appuser /app
USER appuser
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD python -c "import urllib.request,sys; \
sys.exit(0 if urllib.request.urlopen('http://localhost:8000/api/v1/health/live').status==200 else 1)"
# Default command runs the API; the ML service overrides with app.ml_main:app.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
