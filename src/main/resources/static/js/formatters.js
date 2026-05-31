/* ═══════════════════════════════════════════
   Portfolio Tracker — Formatters & Calc Helpers
   ═══════════════════════════════════════════ */

var COLORS = window.__COLORS__ || (window.__COLORS__ = ['#4f98a3','#e8af34','#6daa45','#5591c7','#fdab43','#a86fdf','#dd6974','#bb653b','#d163a7','#01696f']);

function calcInvested(p) { return p.shares * p.avgPrice; }
function calcValue(p) { return p.currentPrice ? p.shares * p.currentPrice : null; }
function calcPL(p) { const v = calcValue(p); return v !== null ? v - calcInvested(p) : null; }
function calcPLPct(p) { const pl = calcPL(p); const inv = calcInvested(p); return pl !== null && inv > 0 ? (pl / inv) * 100 : null; }

function totalInvested(arr) { return (arr || []).reduce((s, p) => s + calcInvested(p), 0); }
function totalValue(arr) {
  const w = (arr || []).filter(p => p.currentPrice);
  return w.length > 0 ? w.reduce((s, p) => s + calcValue(p), 0) : null;
}
function totalPL(arr) {
  const tv = totalValue(arr);
  const ti = (arr || []).filter(p => p.currentPrice).reduce((s, p) => s + calcInvested(p), 0);
  return tv !== null ? tv - ti : null;
}

function fmtEur(v, d) {
  d = d !== undefined ? d : 2;
  if (v === null || v === undefined || isNaN(v)) return '—';
  return v.toLocaleString('es-ES', { minimumFractionDigits: d, maximumFractionDigits: d }) + ' €';
}

function fmtPct(v) {
  if (v === null || v === undefined || isNaN(v)) return '—';
  return (v >= 0 ? '+' : '') + v.toFixed(2) + '%';
}

function fmtShares(v) {
  if (v === null || v === undefined) return '—';
  return v.toLocaleString('es-ES', { minimumFractionDigits: 2, maximumFractionDigits: 6 });
}

function plClass(v) {
  return v === null ? 'pl-zero' : v > 0 ? 'pl-positive' : v < 0 ? 'pl-negative' : 'pl-zero';
}

function fmtK(v) {
  if (v === null || v === undefined) return '—';
  if (Math.abs(v) >= 1000000) return (v / 1000000).toFixed(1) + 'M';
  if (Math.abs(v) >= 1000) return (v / 1000).toFixed(1) + 'K';
  return v.toFixed(0);
}
