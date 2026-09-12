# syntax=docker/dockerfile:1
# --- Multi-stage build for the Symfony API (single-process FrankenPHP). --------

# 1) Vendor dependencies (cached separately from app code).
# Builder stage: extensions (amqp/pdo_pgsql/intl) are absent here but present in
# the runtime image, so ignore platform reqs while installing the locked deps.
FROM composer:2 AS vendor
WORKDIR /app
COPY composer.json composer.lock* ./
RUN composer install --no-dev --no-scripts --prefer-dist --no-progress --no-interaction --ignore-platform-reqs \
    || composer install --no-dev --no-scripts --prefer-dist --no-progress --no-interaction --ignore-platform-reqs --no-cache

# 2) Runtime image.
FROM dunglas/frankenphp:1-php8.3 AS runtime
ENV APP_ENV=prod

# PHP extensions required by the stack (Postgres, AMQP, intl, opcache).
RUN install-php-extensions pdo_pgsql intl opcache amqp zip

WORKDIR /app
COPY --from=vendor /app/vendor ./vendor
COPY . .

# Warm the cache and run as non-root. FrankenPHP/Caddy needs its data & config
# dirs writable by the runtime user (it provisions a local CA on boot).
RUN php bin/console cache:warmup --env=prod \
    && mkdir -p /data/caddy /config/caddy \
    && chown -R www-data:www-data var /data /config

USER www-data
EXPOSE 8000

# Liveness/readiness probes hit /health/live and /health/ready (see HealthController).
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s \
    CMD curl -fsS http://localhost:8000/health/live || exit 1

# FrankenPHP serves the Symfony app via the runtime worker.
ENV SERVER_NAME=:8000
CMD ["frankenphp", "run", "--config", "/etc/caddy/Caddyfile"]
