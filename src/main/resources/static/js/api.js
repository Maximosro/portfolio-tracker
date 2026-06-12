/* ═══════════════════════════════════════════
   Portfolio Tracker — API Fetch Wrappers
   ═══════════════════════════════════════════ */

var BASE = window.__BASE__ || (window.__BASE__ = (function() {
  // Remove filename and trailing slash from path to get clean base URL
  var path = window.location.pathname.replace(/\/[^/]*\.[^/]*$/, '').replace(/\/+$/, '') || '';
  return window.location.origin + path;
})());
window.__CONTEXT_PATH__ = window.__CONTEXT_PATH__ || '/portfoliotracker';

/**
 * Wraps fetch() adding the Supabase Authorization header.
 * On 401, attempts a single session refresh and retries.
 * If still unauthorized, redirects to login.
 */
async function authFetch(url, options) {
  options = options || {};
  options.headers = options.headers || {};

  var token = await getAccessToken();
  if (!token) {
    window.location.replace((window.__CONTEXT_PATH__ || '/portfoliotracker') + '/login.html');
    throw new Error('No autenticado');
  }
  options.headers['Authorization'] = 'Bearer ' + token;

  var resp = await fetch(url, options);

  if (resp.status === 401) {
    // Attempt session refresh once
    try {
      if (typeof initSupabase === 'function') {
        var client = initSupabase();
        if (client) {
          var refreshResp = await client.auth.refreshSession();
          if (refreshResp.data && refreshResp.data.session) {
            options.headers['Authorization'] = 'Bearer ' + refreshResp.data.session.access_token;
            return await fetch(url, options);
          }
        }
      }
    } catch (e) {
      console.warn('authFetch: refresh failed', e);
    }
    window.location.replace((window.__CONTEXT_PATH__ || '/portfoliotracker') + '/login.html');
    throw new Error('Sesión expirada');
  }

  return resp;
}

// ── Positions ──
async function apiGet() {
  const resp = await authFetch(`${BASE}/api/positions`);
  if (!resp.ok) throw new Error('Error cargando posiciones');
  return resp.json();
}
async function apiCreate(position) {
  const resp = await authFetch(`${BASE}/api/positions`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(position)
  });
  if (!resp.ok) { const err = await resp.json().catch(() => ({})); throw new Error(err.error || 'Error creando posición'); }
  return resp.json();
}
async function apiUpdate(ticker, position) {
  const resp = await authFetch(`${BASE}/api/positions/${encodeURIComponent(ticker)}`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(position)
  });
  if (!resp.ok) { const err = await resp.json().catch(() => ({})); throw new Error(err.error || 'Error actualizando posición'); }
  return resp.json();
}
async function apiDelete(ticker) {
  const resp = await authFetch(`${BASE}/api/positions/${encodeURIComponent(ticker)}`, { method: 'DELETE' });
  if (!resp.ok) throw new Error('Error eliminando posición');
}

// ── DCA ──
async function apiDcaGetAll() {
  const resp = await authFetch(`${BASE}/api/dca`);
  if (!resp.ok) throw new Error('Error cargando DCA');
  return resp.json();
}
async function apiDcaCreate(entry) {
  const resp = await authFetch(`${BASE}/api/dca`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(entry)
  });
  if (!resp.ok) { const err = await resp.json().catch(() => ({})); throw new Error(err.error || 'Error en operación DCA'); }
  return resp.json();
}
async function apiDcaDelete(id) {
  const resp = await authFetch(`${BASE}/api/dca/${id}`, { method: 'DELETE' });
  if (!resp.ok) throw new Error('Error eliminando operación');
}

// ── Watchlist ──
async function apiWatchlistGetAll() {
  const resp = await authFetch(`${BASE}/api/watchlist`);
  if (!resp.ok) throw new Error('Error cargando watchlist');
  return resp.json();
}
async function apiWatchlistCreate(item) {
  const resp = await authFetch(`${BASE}/api/watchlist`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(item)
  });
  if (!resp.ok) throw new Error('Error añadiendo a watchlist');
  return resp.json();
}
async function apiWatchlistDelete(id) {
  const resp = await authFetch(`${BASE}/api/watchlist/${id}`, { method: 'DELETE' });
  if (!resp.ok) throw new Error('Error eliminando de watchlist');
}
async function apiWatchlistRefresh() {
  const resp = await authFetch(`${BASE}/api/watchlist/refresh`, { method: 'POST' });
  if (!resp.ok) throw new Error('Error refrescando watchlist');
  return resp.json();
}

// ── Alerts ──
async function apiAlertsGet() {
  const resp = await authFetch(`${BASE}/api/alerts`);
  if (!resp.ok) throw new Error('Error cargando alertas');
  return resp.json();
}

// ── Metrics ──
async function apiMetricsGet() {
  const resp = await authFetch(`${BASE}/api/metrics`);
  if (!resp.ok) throw new Error('Error cargando métricas');
  return resp.json();
}

// ── Prices / History ──
async function apiPriceHistory(ticker, range) {
  const resp = await authFetch(`${BASE}/api/prices/history/${encodeURIComponent(ticker)}?range=${range || '1m'}`);
  if (!resp.ok) throw new Error('Error cargando histórico');
  return resp.json();
}
async function apiPortfolioHistory(range) {
  const resp = await authFetch(`${BASE}/api/prices/portfolio-history?range=${range || '1m'}`);
  if (!resp.ok) throw new Error('Error cargando histórico de cartera');
  return resp.json();
}
async function apiForceRefresh() {
  const resp = await authFetch(`${BASE}/api/prices/refresh`, { method: 'POST' });
  if (!resp.ok) throw new Error('Error forzando actualización');
  return resp.json();
}

// ── Investment Plan ──
async function apiInvestmentPlanGet() {
  const resp = await authFetch(`${BASE}/api/investment-plan`);
  if (!resp.ok) throw new Error('Error cargando plan');
  return resp.json();
}
async function apiInvestmentPlanSave(plan) {
  const resp = await authFetch(`${BASE}/api/investment-plan`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(plan)
  });
  if (!resp.ok) throw new Error('Error guardando plan');
  return resp.json();
}

// ── Export ──
async function apiExportReport() {
  const resp = await authFetch(`${BASE}/api/export/report`);
  if (!resp.ok) throw new Error('Error generando informe');
  return resp.text();
}

// ── Activity Log ──
async function apiActivityLog(category, limit) {
  let url = `${BASE}/api/activity-log?limit=${limit || 200}`;
  if (category && category !== 'ALL') url += `&category=${category}`;
  const resp = await authFetch(url);
  if (!resp.ok) throw new Error('Error cargando log');
  return resp.json();
}

// ── Simulator (runs from simuladores.html and mobile) ──
async function apiSimulator(endpoint, body) {
  const resp = await authFetch(`${BASE}/api/simulator/${endpoint}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (!resp.ok) throw new Error('Error en simulación');
  return resp.json();
}
