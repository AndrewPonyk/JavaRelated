# syntax=docker/dockerfile:1
FROM python:3.12-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY crypto_toolkit/ ./crypto_toolkit/
COPY migrations/ ./migrations/

USER app
EXPOSE 5000

HEALTHCHECK --interval=30s --timeout=3s CMD python -c "import urllib.request;urllib.request.urlopen('http://127.0.0.1:5000/api/health')"

CMD ["gunicorn", "-c", "gunicorn.conf.py", "crypto_toolkit.app:create_app()"]
