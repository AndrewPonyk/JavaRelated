#!/bin/sh
set -eu

: "${DATABASE_USER:?DATABASE_USER is required}"
: "${DATABASE_PASSWORD:?DATABASE_PASSWORD is required}"

case "$DATABASE_USER" in
  *[!a-zA-Z0-9_]*)
    echo "DATABASE_USER may contain only letters, digits, and underscores." >&2
    exit 1
    ;;
esac

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
  --set=database_user="$DATABASE_USER" \
  --set=database_password="$DATABASE_PASSWORD" \
  --set=database_name="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"database_user"
  LOGIN
  PASSWORD :'database_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION;

GRANT CONNECT ON DATABASE :"database_name" TO :"database_user";
GRANT USAGE ON SCHEMA public TO :"database_user";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"database_user";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"database_user";
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO :"database_user";

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"database_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"database_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO :"database_user";
SQL
