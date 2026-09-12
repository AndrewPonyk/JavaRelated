# syntax=docker/dockerfile:1
# ──────────────────────────────────────────────────────────────────────────
# Multi-stage production image for the Laravel API (php-fpm).
# The same image runs as web / worker / scheduler — only the command differs.
#
# Build context is the REPO ROOT (see docker-compose.yml) so the root-level
# docker/ and scripts/ directories are reachable; the Laravel app lives under
# backend/. The optimized, classmap-authoritative autoloader is generated in
# the composer stage (the only stage that has both composer and the source).
# ──────────────────────────────────────────────────────────────────────────

# ── Stage 1: Composer install + optimized autoloader ──────────────────────
FROM composer:2 AS vendor
WORKDIR /app
# Full app source (incl. composer.json/lock) so --classmap-authoritative can
# index App\ classes, not just vendor.
COPY backend/ ./
RUN composer install \
        --no-dev \
        --prefer-dist \
        --no-interaction \
        --no-scripts \
        --optimize-autoloader \
        --classmap-authoritative

# ── Stage 2: Runtime ──────────────────────────────────────────────────────
FROM php:8.3-fpm-alpine AS runtime

# System deps + PHP extensions required by the stack.
RUN apk add --no-cache \
        icu-dev \
        oniguruma-dev \
        $PHPIZE_DEPS \
    && docker-php-ext-install -j"$(nproc)" \
        pdo_mysql \
        bcmath \
        intl \
        opcache \
    && pecl install redis \
    && docker-php-ext-enable redis \
    && apk del $PHPIZE_DEPS

# Production OPcache tuning.
COPY docker/php/php.ini /usr/local/etc/php/conf.d/zz-app.ini

WORKDIR /var/www/html

# App source + vendored deps with the already-optimized autoloader.
COPY --from=vendor /app ./
RUN chown -R www-data:www-data storage bootstrap/cache

# Entrypoint runs config/route cache, restarts workers, then the given CMD.
COPY scripts/entrypoint.sh /usr/local/bin/entrypoint
RUN chmod +x /usr/local/bin/entrypoint

EXPOSE 9000
ENTRYPOINT ["entrypoint"]
CMD ["php-fpm"]
