/* ═══════════════════════════════════════════
   Portfolio Tracker — API Fetch Wrappers
   ═══════════════════════════════════════════ */

var BASE = window.__BASE__ || (window.__BASE__ = document.baseURI.replace(/\/+$/, ''));

// ── Positions ──
async function apiGet() {
  const resp = await fetch(`${BASE}/api/positions`);
  if (!resp.ok) throw new Error('Error cargando posiciones');
  return resp.json();
}
async function apiCreate(position) {
  const resp = await fetch(`${BASE}/api/positions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(position)
  });
  if (!resp.ok) { const err = await resp.json().catch(() => ({})); throw new Error(err.error || 'Error creando posición'); }
  return resp.json();
}
async function apiUpdate(ticker, position) {
  const resp = await fetch(`${BASE}/api/positions/${encodeURIComponent(ticker)}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(position)
  });
  if (!resp.ok) { const err = await resp.json().catch(() => ({})); throw new Error(err.error || 'Error actualizando posición'); }
  return resp.json();
}
async function apiDelete(ticker) {
  const resp = await fetch(`${BASE}/api/positions/${encodeURIComponent(ticker)}`, { method: 'DELETE' });
  if (!resp.ok) throw new Error('Error eliminando posición');
}

// ── DCA ──
async function apiDcaGetAll() {
  const resp = await fetch(`${BASE}/api/dca`);
  if (!resp.ok) throw new Error('Error cargando DCA');
  return resp.json();
}
async function apiDcaCreate(entry) {
  const resp = await fetch(`${BASE}/api/dca`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(entry)
  });
  if (!resp.ok) { const err = await resp.json().catch(() => ({})); throw new Error(err.error || 'Error en operación DCA'); }
  return resp.json();
}
async function apiDcaDelete(id) {
  const resp = await fetch(`${BASE}/api/dca/${id}`, { method: 'DELETE' });
  if (!resp.ok) throw new Error('Error eliminando operación');
}

// ── Watchlist ──
async function apiWatchlistGetAll() {
  const resp = await fetch(`${BASE}/api/watchlist`);
  if (!resp.ok) throw new Error('Error cargando watchlist');
  return resp.json();
}
async function apiWatchlistCreate(item) {
  const resp = await fetch(`${BASE}/api/watchlist`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(item)
  });
  if (!resp.ok) throw new Error('Error añadiendo a watchlist');
  return resp.json();
}
async function apiWatchlistDelete(id) {
  const resp = await fetch(`${BASE}/api/watchlist/${id}`, { method: 'DELETE' });
  if (!resp.ok) throw new Error('Error eliminando de watchlist');
}
async function apiWatchlistRefresh() {
  const resp = await fetch(`${BASE}/api/watchlist/refresh`, { method: 'POST' });
  if (!resp.ok) throw new Error('Error refrescando watchlist');
  return resp.json();
}

// ── Alerts ──
async function apiAlertsGet() {
  const resp = await fetch(`${BASE}/api/alerts`);
  if (!resp.ok) throw new Error('Error cargando alertas');
  return resp.json();
}

// ── Metrics ──
async function apiMetricsGet() {
  const resp = await fetch(`${BASE}/api/metrics`);
  if (!resp.ok) throw new Error('Error cargando métricas');
  return resp.json();
}

// ── Prices / History ──
async function apiPriceHistory(ticker, range) {
  const resp = await fetch(`${BASE}/api/prices/history/${encodeURIComponent(ticker)}?range=${range || '1m'}`);
  if (!resp.ok) throw new Error('Error cargando histórico');
  return resp.json();
}
async function apiPortfolioHistory(range) {
  const resp = await fetch(`${BASE}/api/prices/portfolio-history?range=${range || '1m'}`);
  if (!resp.ok) throw new Error('Error cargando histórico de cartera');
  return resp.json();
}
async function apiForceRefresh() {
  const resp = await fetch(`${BASE}/api/prices/refresh`, { method: 'POST' });
  if (!resp.ok) throw new Error('Error forzando actualización');
  return resp.json();
}

// ── Investment Plan ──
async function apiInvestmentPlanGet() {
  const resp = await fetch(`${BASE}/api/investment-plan`);
  if (!resp.ok) throw new Error('Error cargando plan');
  return resp.json();
}
async function apiInvestmentPlanSave(plan) {
  const resp = await fetch(`${BASE}/api/investment-plan`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(plan)
  });
  if (!resp.ok) throw new Error('Error guardando plan');
  return resp.json();
}

// ── Export ──
async function apiExportReport() {
  const resp = await fetch(`${BASE}/api/export/report`);
  if (!resp.ok) throw new Error('Error generando informe');
  return resp.text();
}

// ── Activity Log ──
async function apiActivityLog(category, limit) {
  let url = `${BASE}/api/activity-log?limit=${limit || 200}`;
  if (category && category !== 'ALL') url += `&category=${category}`;
  const resp = await fetch(url);
  if (!resp.ok) throw new Error('Error cargando log');
  return resp.json();
}

// ── Simulator (runs from simuladores.html and mobile) ──
async function apiSimulator(endpoint, body) {
  const resp = await fetch(`${BASE}/api/simulator/${endpoint}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!resp.ok) throw new Error('Error en simulación');
  return resp.json();
}
