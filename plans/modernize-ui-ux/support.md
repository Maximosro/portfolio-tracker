# Support: modernize-ui-ux

## Seguimiento de tareas

| Tarea | Estado | Prueba | Notas |
|-------|--------|--------|-------|
| T1.1 — tokens.css | ✅ OK | Verificado con curl | Design tokens nuevos, --font-body alias añadido |
| T1.2 — desktop.css | ✅ OK | Verificado visualmente (Playwright) | Faltan algunas clases (detail-*, tg-*, wl-item-*), pero el CSS base funciona |
| T1.3 — mobile.css | ✅ OK | — | Creado pero no integrado aún en mobile.html |
| T2.1 — formatters.js | ✅ OK | — | Extraído del inline JS, sin conflictos |
| T2.2 — api.js | ✅ OK | — | Se solucionó conflicto de `const` con `var` + guard |
| T2.3 — charts.js | ✅ OK | — | Helper functions extraídas |
| T3.1 — alpine-store.js | ✅ OK | Probado con Playwright | Arreglado `matchMedia` → `window.matchMedia`, `theme_pref` key |
| T3.2 — index.html refactor | ✅ OK | Probado con Playwright | Links CSS/JS externos añadidos, `<style>` tag recuperado |
| T3.3 — Header minimalista | ✅ OK | Probado con Playwright | Funciona: ☰ drawer, alertas, tema |
| T3.4 — Modales Alpine.js | ⏳ Pendiente | — | Siguiente iteración |
| T3.5 — Toasts Alpine.js | ⏳ Pendiente | — | Siguiente iteración |
| T4.1 — mobile.html refactor | ⏳ Pendiente | — | Siguiente iteración |
| T4.2 — Sparklines mobile | ⏳ Pendiente | — | Siguiente iteración |
| T5.1 — simuladores.html | ⏳ Pendiente | — | Siguiente iteración |
| T5.2 — Verificación completa | 🔄 Parcial | Playwright smoke test | Drawer, tema, ESC funcionan. KPIs y tablas renderizan. |

## Bugs encontrados y solucionados
- **`const` redeclaración**: api.js y formatters.js declaraban `const BASE` y `const COLORS` que colisionaban con el script inline. Solución: `var` + guard `window.__BASE__`.
- **`matchMedia` typo**: alpine-store.js tenía `matchMedia` (una 'd') en vez de `window.matchMedia`. Causaba ReferenceError silencioso que impedía toda inicialización de Alpine.
- **`<style>` tag perdido**: La sesión anterior eliminó el `<style>` tag del head al modificar los links. Solución: re-añadido.
- **Alpine `x-data` en `<html>`**: Alpine no procesa directivas en el `<html>` element. Solución: mover `x-data="app"` al `<div class="app">`.

## Mejoras propuestas
- Extraer todo el CSS inline restante a desktop.css (ahora solo se eliminaron los tokens duplicados)
- Extraer funciones JS duplicadas del script inline (apiGet, apiCreate, formateadores, etc.)
- Usar sparklines SVG en vez de Chart.js para mobile (más ligero)
- Añadir focus-trap al drawer para accesibilidad
