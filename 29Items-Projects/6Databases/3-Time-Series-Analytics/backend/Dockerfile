# Backend images, two targets from one context:
#   api    (default) — slim FastAPI image; detection falls back to z-score
#   worker           — api + Prophet/pandas for forecast-band detection
# Compose/CI build the worker target for anomaly-worker/downsampler services.
FROM python:3.12-slim AS api

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /srv/tsa

COPY pyproject.toml ./
COPY app ./app

RUN pip install . \
    && addgroup --system tsa && adduser --system --ingroup tsa tsa
USER tsa

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]

FROM api AS worker
USER root
RUN pip install ".[ml]"
USER tsa
CMD ["python", "-m", "app.workers.anomaly_worker"]
