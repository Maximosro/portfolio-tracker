/* ═══════════════════════════════════════════
   Portfolio Tracker — Chart Helpers
   ═══════════════════════════════════════════ */

function chartColors() {
  const style = getComputedStyle(document.documentElement);
  return {
    bg: style.getPropertyValue('--color-bg').trim(),
    surface: style.getPropertyValue('--color-surface').trim(),
    text: style.getPropertyValue('--color-text').trim(),
    textMuted: style.getPropertyValue('--color-text-muted').trim(),
    border: style.getPropertyValue('--color-border').trim(),
    primary: style.getPropertyValue('--color-primary').trim(),
    success: style.getPropertyValue('--color-success').trim(),
    notification: style.getPropertyValue('--color-notification').trim(),
    gold: style.getPropertyValue('--color-gold').trim(),
    tooltipBg: 'rgba(22,25,33,0.95)'
  };
}

function destroyChart(name) {
  if (window[name]) { window[name].destroy(); window[name] = null; }
}

function sparkConfig(data, color) {
  return {
    type: 'line',
    data: {
      labels: data.map((_, i) => i),
      datasets: [{
        data,
        borderColor: color,
        borderWidth: 1.5,
        pointRadius: 0,
        tension: 0.35,
        fill: false
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: { x: { display: false }, y: { display: false } },
      plugins: { legend: { display: false }, tooltip: { enabled: false } },
      elements: { line: { borderJoinStyle: 'round' } }
    }
  };
}

function calcYBounds10(datasets, range) {
  let min = Infinity, max = -Infinity;
  for (const ds of datasets) {
    for (const pt of ds.data) {
      if (pt.y == null) continue;
      if (pt.y < min) min = pt.y;
      if (pt.y > max) max = pt.y;
    }
  }
  if (min === Infinity) return { min: 0, max: 100 };
  const pad = (max - min) * 0.1 || 10;
  return { min: min - pad, max: max + pad };
}
