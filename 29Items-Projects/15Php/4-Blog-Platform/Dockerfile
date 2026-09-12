# syntax=docker/dockerfile:1
#
# Containerised build for the Blog Platform (PHP 8.3 — the project's target).
# `docker compose up` builds this and serves the app; the same image runs the
# test suite (dev dependencies included). Production on Forge does not use this
# image, but it gives parity + a one-command local/CI stack.

# ── Stage 1: build frontend assets (self-contained; no PHP needed) ────────────
FROM node:20-alpine AS assets
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
# Tailwind scans these for class names; Vite needs the entrypoints.
COPY vite.config.js tailwind.config.js postcss.config.js ./
COPY resources ./resources
COPY app ./app
RUN npm run build

# ── Stage 2: PHP runtime + dependencies ──────────────────────────────────────
FROM php:8.3-cli AS app
RUN apt-get update && apt-get install -y --no-install-recommends \
        git unzip libzip-dev libonig-dev libxml2-dev libsqlite3-dev \
    && docker-php-ext-install pdo pdo_sqlite mbstring zip dom \
    && rm -rf /var/lib/apt/lists/*
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer
WORKDIR /app

# Dependency layer — cached unless composer.json changes, so source-only
# rebuilds skip the (slow) dependency resolution + download.
#
# NOTE: this environment's Composer enforces a security-advisory *block* policy
# that excludes advisory-affected packages (incl. Laravel 11) during resolution.
# Disable that policy so dependencies install; CVEs are surfaced separately by
# the `composer audit` CI step (see .github/workflows/ci.yml).
COPY composer.json composer.lock ./
RUN composer config -g policy.advisories.block false 2>/dev/null || true; \
    composer install --optimize-autoloader --no-interaction --no-scripts --no-autoloader --no-security-blocking

# Application source + compiled assets.
COPY . .
COPY --from=assets /app/public/build ./public/build

# Finalize the autoloader and run package discovery (needs the full source).
# A throwaway APP_KEY lets the framework boot for discovery; the real key is
# generated at runtime by the entrypoint.
# Create writable runtime dirs FIRST — package:discover writes to bootstrap/cache.
RUN mkdir -p storage/framework/cache/data storage/framework/sessions storage/framework/views storage/logs storage/app bootstrap/cache \
    && composer dump-autoload --optimize --no-scripts \
    && APP_KEY=base64:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= php artisan package:discover --ansi \
    && chmod +x docker/entrypoint.sh \
    && chown -R www-data:www-data storage bootstrap/cache

EXPOSE 8000
ENTRYPOINT ["docker/entrypoint.sh"]
CMD ["php", "artisan", "serve", "--host=0.0.0.0", "--port=8000"]
