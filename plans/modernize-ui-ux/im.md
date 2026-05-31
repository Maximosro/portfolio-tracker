# Implement: modernize-ui-ux

## Rama
`feature/modernize-ui-ux`

## Cambios realizados
| Archivo | Acción | Descripción |
|---------|--------|-------------|
| `css/tokens.css` | CREAR | Design tokens compartidos (DM Sans + JetBrains Mono, paleta teal #3cc9b4) |
| `css/desktop.css` | CREAR | Estilos desktop: reset, layout, header, drawer, KPI, tablas, modales, paneles, responsive |
| `css/mobile.css` | CREAR | Estilos mobile: keycards, summary card, KPI chips, tab bar |
| `js/formatters.js` | CREAR | Funciones de formato y cálculos: fmtEur, fmtPct, fmtShares, plClass, etc. |
| `js/api.js` | CREAR | Fetch wrappers para todos los endpoints REST |
| `js/charts.js` | CREAR | Helpers de Chart.js: chartColors, destroyChart, sparkConfig, calcYBounds10 |
| `js/alpine-store.js` | CREAR | Store Alpine.js: theme, drawer, ESC cierra todos los modales, keyboard shortcuts |
| `index.html` | MODIFICAR | Header minimalista + drawer Alpine.js, tokens CSS externos, elim IIFE theme vanilla, modales con click-outside y soporte ESC global |
| `mobile.html` | MODIFICAR | Head actualizado (tokens.css, mobile.css, Alpine.js, shared JS), tokens duplicados eliminados, `const` conflicts resueltos |
| `simuladores.html` | MODIFICAR | Link a tokens.css, tokens duplicados eliminados, fuentes actualizadas (DM Sans + JetBrains Mono) |

## PR
Pendiente

## Notas
- Alpine.js 3.14.1 integrado vía CDN. Funcionando: drawer (abrir/cerrar con click y ESC), theme toggle (claro/oscuro con localStorage), keyboard shortcuts (⌘K, ⌘N, ⌘⇧N), ESC cierra todos los modales.
- Se añadió click-outside a los 5 modales que no lo tenían (position, DCA, import, watchlist, wlAlert).
- Mobile y simuladores ahora comparten tokens.css con desktop. Sin errores JS (verificado con Playwright).
- Pendiente para próxima iteración: convertir modales a x-show/x-model Alpine.js, rediseñar mobile.html a keycards con sparklines.
