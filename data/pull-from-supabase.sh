#!/bin/bash
# pull-from-supabase.sh — Vuelca Supabase → PostgreSQL local Docker
# Uso: SUPABASE_DB_PASSWORD="tuPassword" bash data/pull-from-supabase.sh

set -e

SUPABASE_URL="postgresql://postgres@db.agtkcnxmlbccbwmsuxdz.supabase.co:6543/postgres?sslmode=require"
LOCAL_CONTAINER="portfolio-tracker-postgres-1"
LOCAL_DB="portfoliodb"
LOCAL_USER="portfolio"

if [ -z "$SUPABASE_DB_PASSWORD" ]; then
  echo "ERROR: Define SUPABASE_DB_PASSWORD"
  exit 1
fi

echo "==> Volcando datos desde Supabase..."
DUMP_FILE=$(mktemp)
export PGPASSWORD="$SUPABASE_DB_PASSWORD"

docker run --rm --network host -e PGPASSWORD="$SUPABASE_DB_PASSWORD" postgres:17-alpine \
  pg_dump "$SUPABASE_URL" \
  --data-only \
  --inserts \
  --on-conflict-do-nothing \
  --no-owner \
  --no-privileges \
  --no-comments \
  --exclude-table=supabase_migrations* \
  > "$DUMP_FILE"

SIZE=$(wc -c < "$DUMP_FILE")
echo "   OK: $SIZE bytes descargados"

echo "==> Limpiando BD local..."
docker exec -i "$LOCAL_CONTAINER" psql -U "$LOCAL_USER" -d "$LOCAL_DB" << 'SQLEOF'
DO $$ DECLARE r RECORD;
BEGIN
  FOR r IN (SELECT tablename FROM pg_tables WHERE schemaname='public') LOOP
    EXECUTE 'TRUNCATE TABLE ' || quote_ident(r.tablename) || ' CASCADE';
  END LOOP;
END $$;
SQLEOF
echo "   OK: tablas limpiadas"

echo "==> Cargando datos en local..."
docker exec -i "$LOCAL_CONTAINER" psql -U "$LOCAL_USER" -d "$LOCAL_DB" < "$DUMP_FILE"
echo "   OK: datos cargados"

echo "==> Reset secuencias..."
docker exec -i "$LOCAL_CONTAINER" psql -U "$LOCAL_USER" -d "$LOCAL_DB" << 'SQLEOF'
DO $$ DECLARE r RECORD;
BEGIN
  FOR r IN (
    SELECT c.relname as seq_name, t.relname as table_name
    FROM pg_class c JOIN pg_namespace n ON c.relnamespace = n.oid
    JOIN pg_tables t ON t.tablename || '_id_seq' = c.relname
    WHERE n.nspname = 'public' AND c.relkind = 'S'
  ) LOOP
    EXECUTE 'SELECT setval(''' || quote_ident(r.seq_name) || ''', COALESCE((SELECT MAX(id) FROM ' || quote_ident(r.table_name) || '), 1), true)';
  END LOOP;
END $$;
SQLEOF
echo "   OK: secuencias actualizadas"

rm "$DUMP_FILE"

echo ""
echo "✅ Pull completado: Supabase → PostgreSQL local"
echo "   Conectado a localhost:5432 (db: $LOCAL_DB, user: $LOCAL_USER)"
