-- Añade columna cost_basis a dca_history para P&L inmutable en ventas parciales.
-- Fix: costbasis-ventas — https://github.com/Maximosro/portfolio-tracker
--
-- Ejecutar ANTES del deploy en producción (Supabase):
--   1. Abrir Supabase SQL Editor
--   2. Pegar y ejecutar esta consulta
--   3. Verificar: SELECT column_name FROM information_schema.columns WHERE table_name='dca_history' AND column_name='cost_basis';
--
-- En standalone (dev), Hibernate ddl-auto: update añade la columna automáticamente.

ALTER TABLE dca_history ADD COLUMN IF NOT EXISTS cost_basis DOUBLE PRECISION;
