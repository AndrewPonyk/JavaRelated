#!/bin/sh
# Container entrypoint: ensure env + database, run migrations, then exec the CMD.
set -e

cd /app

# 1. Ensure an environment file + app key exist.
if [ ! -f .env ]; then
    cp .env.example .env
fi
if ! grep -q "^APP_KEY=base64:" .env && [ -z "${APP_KEY:-}" ]; then
    php artisan key:generate --force
fi

# 1b. Persist runtime env overrides into .env. `php artisan serve` request
#     workers (and migrate/seed) read .env, and do NOT reliably inherit
#     arbitrary OS env vars set by docker-compose — so sync the important ones.
for var in APP_URL APP_DEBUG DB_CONNECTION DB_DATABASE CACHE_STORE SESSION_DRIVER QUEUE_CONNECTION; do
    val=$(printenv "$var" 2>/dev/null || true)
    [ -n "$val" ] || continue
    if grep -q "^${var}=" .env; then
        sed -i "s|^${var}=.*|${var}=${val}|" .env
    else
        printf '%s=%s\n' "$var" "$val" >> .env
    fi
done

# 2. Ensure the SQLite database file exists (path from DB_DATABASE or default).
DB_FILE="${DB_DATABASE:-/app/database/database.sqlite}"
mkdir -p "$(dirname "$DB_FILE")"
[ -f "$DB_FILE" ] || touch "$DB_FILE"

# 3. Make runtime dirs writable (named volumes may mount in empty).
mkdir -p storage/framework/cache/data storage/framework/sessions storage/framework/views storage/logs storage/app
chmod -R ug+rw storage bootstrap/cache 2>/dev/null || true

# 4. Run migrations (idempotent). Seed only when SEED=true and DB is fresh.
php artisan migrate --force
if [ "${SEED:-false}" = "true" ]; then
    php artisan db:seed --force || true
fi

# 5. Cache config/routes/views in production for speed.
if [ "${APP_ENV:-local}" = "production" ]; then
    php artisan optimize
fi

exec "$@"
