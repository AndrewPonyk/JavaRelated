#!/bin/sh
# Container entrypoint for the Laravel API image.
# Runs once per container start, then hands off to the given CMD (php-fpm,
# queue:work, etc.). See docs/TECH-NOTES.md §3.3.
set -e

# Cache framework config & routes for performance (TECH-NOTES pitfall #3).
# Skipped automatically if already cached in the image build.
php artisan config:cache
php artisan route:cache

# NOTE: Migrations are intentionally NOT run here — running them on every
# replica's boot races across instances (TECH-NOTES pitfall #4). They run as
# a single gated step in deploy.yml. Uncomment ONLY for single-instance/local:
# if [ "${RUN_MIGRATIONS_ON_BOOT:-false}" = "true" ]; then
#     php artisan migrate --force
# fi

# Restart any queue workers so they pick up the new code (pitfall #6).
php artisan queue:restart || true

exec "$@"
