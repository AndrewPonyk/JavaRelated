#!/bin/bash
# |su:49 Container entrypoint: runs BEFORE the main application starts
# Handles: waiting for dependencies, running migrations, creating admin user
# This script runs every time container starts (not just on first build)

set -e  # Exit immediately if any command fails

# Function to wait for a service
wait_for_service() {
    local host=$1
    local port=$2
    local service=$3
    local max_attempts=${4:-30}
    local attempt=0

    echo "Waiting for $service at $host:$port..."
    while [ $attempt -lt $max_attempts ]; do
        if nc -z "$host" "$port" 2>/dev/null; then
            echo "$service is ready!"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 1
    done
    echo "Error: $service at $host:$port is not available after $max_attempts attempts"
    return 1
}

# Parse DATABASE_URL or use individual vars
if [ -n "$DATABASE_URL" ]; then
    # Extract host and port from DATABASE_URL
    DB_HOST=$(echo "$DATABASE_URL" | sed -n 's/.*@\([^:\/]*\).*/\1/p')
    DB_PORT=$(echo "$DATABASE_URL" | sed -n 's/.*:\([0-9]*\)\/.*/\1/p')
    DB_PORT=${DB_PORT:-5432}
else
    DB_HOST=${DB_HOST:-localhost}
    DB_PORT=${DB_PORT:-5432}
fi

# Parse REDIS_URL
if [ -n "$REDIS_URL" ]; then
    REDIS_HOST=$(echo "$REDIS_URL" | sed -n 's/.*\/\/\([^:]*\).*/\1/p')
    REDIS_PORT=$(echo "$REDIS_URL" | sed -n 's/.*:\([0-9]*\).*/\1/p')
    REDIS_PORT=${REDIS_PORT:-6379}
else
    REDIS_HOST=${REDIS_HOST:-localhost}
    REDIS_PORT=${REDIS_PORT:-6379}
fi

# Wait for database
wait_for_service "$DB_HOST" "$DB_PORT" "PostgreSQL" 60

# Wait for Redis
wait_for_service "$REDIS_HOST" "$REDIS_PORT" "Redis" 30

# |su:50 Migration race condition prevention:
# Problem: backend, celery-worker, celery-beat all start simultaneously
# If all run migrations, they conflict (duplicate table creation)
# Solution: only backend runs migrations; celery workers wait 30s
if [[ ! "$1" =~ ^celery ]]; then
    # Ensure migration directories exist
    for app in users products cart checkout inventory vendors recommendations; do
        mkdir -p /app/apps/$app/migrations
        touch /app/apps/$app/migrations/__init__.py
    done

    echo "Generating migrations..."
    python manage.py makemigrations --noinput
    echo "Running database migrations..."
    python manage.py migrate --noinput
else
    echo "Skipping migrations (celery worker - backend handles this)"
    # Wait for backend to finish migrations
    sleep 30
fi

# Create superuser if it doesn't exist (for development)
if [ -n "$DJANGO_SUPERUSER_EMAIL" ] && [ -n "$DJANGO_SUPERUSER_PASSWORD" ]; then
    echo "Creating superuser if not exists..."
    python manage.py shell -c "
from django.contrib.auth import get_user_model
User = get_user_model()
email = '$DJANGO_SUPERUSER_EMAIL'
password = '$DJANGO_SUPERUSER_PASSWORD'
if not User.objects.filter(email=email).exists():
    User.objects.create_superuser(
        email=email,
        password=password,
        first_name='Admin',
        last_name='User'
    )
    print(f'Superuser {email} created')
else:
    print(f'Superuser {email} already exists')
" || echo "Failed to create superuser (may already exist)"
fi

# Collect static files (only in production)
if [ "$DJANGO_SETTINGS_MODULE" = "core.settings.production" ]; then
    echo "Collecting static files..."
    python manage.py collectstatic --noinput
fi

echo "Starting application..."
# |su:51 exec replaces shell process with the application process
# Without exec: shell stays as PID 1, app is child process
# With exec: app becomes PID 1, receives signals (SIGTERM) directly
# Critical for proper container shutdown (docker stop sends SIGTERM to PID 1)
exec "$@"
