-- ============================================================================
-- SEED PRICE_HISTORY — Datos fake para probar la compactación
-- ============================================================================
-- Genera 3 meses de datos con 1 punto cada 10 minutos por cada ticker
-- que exista en la tabla "positions".
--
-- Formato timestamp: "yyyy-MM-dd HH:mm:ss.000000" (compatible con InstantStringConverter)
--
-- USO:
--   1. Parar la app
--   2. Ejecutar en DB Browser for SQLite / DBeaver sobre ./data/portfolio_test.db
--   3. Arrancar la app y probar: POST /portfoliotracker/api/prices/history/purge
-- ============================================================================

-- Limpiar histórico existente
DELETE FROM price_history;

-- Generar datos fake usando WITH RECURSIVE
INSERT INTO price_history (ticker, timestamp, raw_price, currency, price_eur)
WITH RECURSIVE
  tickers AS (
    SELECT
      ticker,
      COALESCE(current_price, avg_price) AS base_price
    FROM positions
  ),
  -- 12960 puntos = 90 días × 24h × 6 (cada 10 min)
  seq(step) AS (
    SELECT 0
    UNION ALL
    SELECT step + 1 FROM seq WHERE step < 12959
  ),
  data AS (
    SELECT
      t.ticker,
      STRFTIME('%Y-%m-%d %H:%M:%S.000000',
        DATETIME('now', '-' || ((12959 - s.step) * 10) || ' minutes')
      ) AS ts,
      -- Precio = base ± variación aleatoria de hasta ~5%
      ROUND(
        t.base_price * (1.0 + (ABS(RANDOM() % 10000) - 5000) / 100000.0),
        4
      ) AS price,
      'EUR' AS currency
    FROM tickers t
    CROSS JOIN seq s
  )
SELECT
  d.ticker,
  d.ts,
  d.price,
  d.currency,
  d.price
FROM data d;

-- Verificar resultado
SELECT
  '=== SEED COMPLETADO ===' AS info,
  COUNT(*) AS total_registros,
  COUNT(DISTINCT ticker) AS tickers,
  MIN(timestamp) AS desde,
  MAX(timestamp) AS hasta
FROM price_history;

