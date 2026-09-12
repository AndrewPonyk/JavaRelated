# sqlmapapi sidecar — built from the official repository.
# (sqlmap publishes no official registry image; building from source keeps the
# supply chain auditable. Pin SQLMAP_REF to a commit/tag for reproducibility.)
FROM python:3.12-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

ARG SQLMAP_REF=master
RUN git clone --depth 1 https://github.com/sqlmapproject/sqlmap.git /opt/sqlmap \
    && cd /opt/sqlmap && git fetch --depth 1 origin "${SQLMAP_REF}" && git checkout FETCH_HEAD

WORKDIR /opt/sqlmap
EXPOSE 8775

# REST-JSON API server consumed by backend/app/services/sqlmap_client.py.
# sqlmapapi mandates Basic auth — credentials injected via env (compose/prod).
# NOTE: no ENTRYPOINT — the shell-form CMD reads env vars (entrypoint would
# prepend itself to CMD and break arg parsing).
ENV SQLMAP_API_USER=sqlmap \
    SQLMAP_API_PASSWORD=dev-sqlmap-pass
CMD ["sh", "-c", "exec python sqlmapapi.py -s -H 0.0.0.0 -p 8775 --username \"$SQLMAP_API_USER\" --password \"$SQLMAP_API_PASSWORD\""]
