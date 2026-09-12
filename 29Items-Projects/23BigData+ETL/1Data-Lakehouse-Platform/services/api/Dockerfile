# Catalog API — build from the REPO ROOT so migrations ship with the image:
#   docker build -f services/api/Dockerfile .
# The entrypoint applies Alembic migrations, then starts uvicorn.
FROM python:3.12-slim

RUN useradd --create-home --uid 1000 apiuser
WORKDIR /srv

COPY services/api/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY services/api/app ./app
COPY alembic.ini ./alembic.ini
COPY migrations ./migrations
COPY services/api/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

USER apiuser
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
  CMD python -c "import urllib.request as u; u.urlopen('http://127.0.0.1:8000/healthz')" || exit 1

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
