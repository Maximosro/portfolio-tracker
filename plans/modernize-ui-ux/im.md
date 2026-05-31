# Implement: modernize-ui-ux

## Rama
`feature/modernize-ui-ux`

## Cambios realizados

### Fase 1-2 — Extracción CSS/JS (sesión anterior)
| Archivo | Acción | Descripción |
|---------|--------|-------------|
| `css/tokens.css` | CREAR | Design tokens compartidos (DM Sans + JetBrains Mono, paleta teal #3cc9b4) |
| `css/desktop.css` | CREAR | Estilos desktop: reset, layout, header, drawer, KPI, tablas, modales, paneles, responsive |
| `css/mobile.css` | CREAR | Estilos mobile: keycards, summary card, KPI chips, tab bar, position cards |
| `js/formatters.js` | CREAR | Funciones de formato y cálculos: fmtEur, fmtPct, fmtShares, plClass, etc. |
| `js/api.js` | CREAR | Fetch wrappers para todos los endpoints REST |
| `js/charts.js` | CREAR | Helpers de Chart.js: chartColors, destroyChart, sparkConfig, calcYBounds10 |
| `js/alpine-store.js` | CREAR | Store Alpine.js: theme, drawer, modales, toasts, keyboard shortcuts |

### Fase 3 — Alpine.js + refactor desktop (sesión actual)
| Archivo | Acción | Descripción |
|---------|--------|-------------|
| `index.html` | MODIFICAR | Header minimalista + drawer Alpine.js, tema migrado a Alpine, modales con `x-model` + `@click.self` + preview reactivo, toasts con `x-for` + `x-transition`, ESC global para todos los modales |
| `alpine-store.js` | MODIFICAR | Añadidos `posForm`, `dcaForm`, `toast()` con auto-removal, ESC cierra todos los modales, `closeModal()` sincronizado |
| `desktop.css` | MODIFICAR | Añadida animación slide al drawer |
| `mobile.html` | MODIFICAR | Header rediseñado, tab bar con nuevas clases CSS, tokens duplicados eliminados, shared JS integrado |
| `simuladores.html` | MODIFICAR | Link a tokens.css, tokens duplicados eliminados, fuentes actualizadas |

## PR
Pendiente

## Bugs corregidos
- `matchMedia` typo → `window.matchMedia` (rompía toda la inicialización de Alpine)
- `const` redeclaración entre api.js/formatters.js y scripts inline
- `<style>` tag perdido en index.html
- Modales fuera del scope Alpine (`x-data` movido a `<body>`)
- `x-transition.opacity.duration.200ms` → `.duration.200` (formato inválido para Alpine)

## Notas
- Alpine.js 3.14.1 integrado. Drawer, tema, toasts, modales y keyboard shortcuts funcionando.
- Enfoque híbrido para modales: JS vanilla controla visibilidad, Alpine gestiona `x-model`, `@click.self`, preview reactivo.
- 0 errores JS en las 3 páginas (verificado con Playwright).
