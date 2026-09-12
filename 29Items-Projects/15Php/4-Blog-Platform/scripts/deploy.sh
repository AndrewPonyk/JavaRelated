#!/usr/bin/env bash
#
# Laravel Forge deployment script.
# Paste this into the Forge site's "Deploy Script", or invoke it from there.
# Assumes zero-downtime deployments are enabled (Forge symlinks releases).

set -euo pipefail

echo "→ Pulling latest code"
git pull origin main

echo "→ Installing PHP dependencies (production)"
composer install --no-interaction --prefer-dist --optimize-autoloader --no-dev

# NOTE: the SQLite database file lives OUTSIDE the release dir (see TECH-NOTES §3.3)
# so atomic release swaps never wipe it. Ensure it exists once:
DB_PATH="${FORGE_SITE_PATH:-$PWD}/storage/app/database.sqlite"
[ -f "$DB_PATH" ] || touch "$DB_PATH"

echo "→ Running migrations"
php artisan migrate --force

echo "→ Caching config, routes & views"
php artisan optimize

echo "→ Ensuring storage symlink"
php artisan storage:link || true

# If using Octane, reload workers; otherwise reload PHP-FPM (Forge handles FPM).
if php artisan list | grep -q "octane:reload"; then
  echo "→ Reloading Octane workers"
  php artisan octane:reload
fi

echo "✓ Deploy complete"
