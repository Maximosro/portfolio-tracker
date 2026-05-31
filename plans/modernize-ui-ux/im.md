# Implement: modernize-ui-ux

## Rama
`feature/modernize-ui-ux`

## Cambios realizados
| Archivo | Acción | Descripción |
|---------|--------|-------------|
| `css/tokens.css` | CREAR | Design tokens compartidos (DM Sans + JetBrains Mono, paleta teal #3cc9b4) |
| `css/desktop.css` | CREAR | Estilos desktop: reset, layout, header, drawer, KPI, tablas, modales, paneles |
| `css/mobile.css` | CREAR | Estilos mobile: keycards, summary card, KPI chips, tab bar (pendiente integrar) |
| `js/formatters.js` | CREAR | Funciones de formato y cálculos: fmtEur, fmtPct, fmtShares, plClass, etc. |
| `js/api.js` | CREAR | Fetch wrappers para todos los endpoints REST (positions, DCA, watchlist, etc.) |
| `js/charts.js` | CREAR | Helpers de Chart.js: chartColors, destroyChart, sparkConfig, calcYBounds10 |
| `js/alpine-store.js` | CREAR | Store Alpine.js: theme, drawer, modals, toasts, keyboard shortcuts (⌘K, ⌘N) |
| `index.html` | MODIFICAR | Header minimalista con drawer Alpine.js, eliminados tokens CSS duplicados, theme toggle migrado a Alpine, añadidos links a CSS/JS externos, Alpine.js CDN, Google Fonts (JetBrains Mono + DM Sans) |

## PR
Pendiente

## Notas
- Alpine.js 3.14.1 integrado vía CDN. Funcionando: drawer (abrir/cerrar con click y ESC), theme toggle (claro/oscuro con localStorage), keyboard shortcuts (⌘K, ⌘N, ⌘⇧N).
- El `x-data` tuvo que moverse del `<html>` al `<div class="app">` porque Alpine no procesaba directivas en el elemento `<html>`.
- Los tokens CSS inline antiguos fueron eliminados para que los nuevos (tokens.css) tomen efecto.
- Se eliminó el IIFE de theme vanilla JS — Alpine maneja el tema completamente.
- Pendiente: convertir modales a Alpine.js, refactorizar mobile.html a keycards, actualizar simuladores.html.
