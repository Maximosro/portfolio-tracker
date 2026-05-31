# Plan: modernize-ui-ux

## Especificación funcional

La interfaz de Portfolio Tracker se reorganiza en tres cambios principales:
1. **Desktop** — El header pasa de 8 botones a solo 3 (menú, alertas, tema). Las acciones se mueven a un nav drawer lateral con atajos de teclado. Tipografía JetBrains Mono para datos y DM Sans para UI. Paleta brutalista-refinada (teal #3cc9b4 sobre fondo oscuro con grid sutil).
2. **Mobile** — Rediseño completo a keycards visuales: cada posición es una tarjeta con icono, sparkline y stats en grid. KPIs en chips scrollables. Summary card con sparkline del portfolio. Watchlist compacta. Tab bar inferior de 5 pestañas.
3. **Arquitectura** — CSS y JS se extraen del monolito `index.html` a archivos independientes. Alpine.js (~15KB CDN) se integra para reactividad declarativa (modales, formularios, transiciones). `simuladores.html` comparte tokens CSS con desktop. `guia.html` queda fuera del scope.

## Diseño técnico

### Arquitectura de archivos

```
src/main/resources/static/
├── css/
│   ├── tokens.css          ← Design tokens (dark/light), compartido por todos
│   ├── desktop.css         ← Estilos desktop: layout, header, nav, kpi, tablas, modales
│   └── mobile.css          ← Estilos mobile: keycards, chips, tabbar, sparklines
├── js/
│   ├── formatters.js       ← fmtEur, fmtPct, fmtShares, plClass, etc.
│   ├── api.js              ← Fetch wrappers para /api/positions, /api/dca, etc.
│   ├── alpine-store.js     ← Alpine.js store: theme, drawer, modals, toasts, forms
│   └── charts.js           ← Chart.js config helpers y funciones de sparkline
├── index.html              ← REFACTORIZADO: solo HTML + Alpine.js bindings
├── mobile.html             ← REFACTORIZADO: keycards + Alpine.js + sparklines
├── simuladores.html        ← MODIFICADO: link a tokens.css, eliminar CSS duplicado
└── guia.html               ← SIN CAMBIOS
```

### Dependencias CDN nuevas
- Alpine.js 3.14.1: `<script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.14.1/dist/cdn.min.js"></script>`
- Google Fonts: `JetBrains Mono` + `DM Sans` (reemplazan Inter para desktop)

### Alpine.js Store (`alpine-store.js`)

```javascript
function app() {
  return {
    // Theme
    theme: localStorage.getItem('theme') || 'dark',
    toggleTheme() { ... },

    // Drawer
    drawerOpen: false,

    // Modal state — 'position' | 'dca' | 'detail' | 'import' | null
    modal: null,
    openModal(type) { this.modal = type; },

    // Position form
    posForm: { ticker: '', name: '', yahooTicker: '', sector: '', shares: '', avgPrice: '', currentPrice: '', color: '' },
    editingTicker: null,

    // DCA form
    dcaForm: { type: 'BUY', ticker: '', shares: '', price: '', date: '' },

    // Data (loaded from API)
    positions: [],
    dcaEntries: [],
    watchlist: [],
    alerts: [],
    metrics: null,
    investmentPlan: null,

    // Toasts
    toasts: [],
    toast(msg, type) { ... },

    // Lifecycle
    async init() { await this.loadAll(); },

    // API
    async loadPositions() { ... },
    async loadDca() { ... },
    async loadAll() { ... },

    // Actions
    async savePosition() { ... },
    async saveDca() { ... },

    // Keyboard shortcuts
    ...
  };
}
```

### Conversión de modales (vanilla → Alpine.js)

| Aspecto | Antes (vanilla) | Después (Alpine.js) |
|---------|----------------|---------------------|
| Mostrar/ocultar | `element.style.display = 'flex'/'none'` | `x-show="modal === 'position'"` |
| Cerrar con ESC | No implementado | `@keydown.escape.window="modal = null"` |
| Cerrar con click fuera | No implementado | `@click.self="modal = null"` en el overlay |
| Campos del form | `document.getElementById('fTicker').value` | `x-model="posForm.ticker"` |
| Vista previa DCA | `updateDcaPreview()` manual con event listeners | `x-text` + `x-show` reactivo |
| Validación | `if (!ticker) showToast(...)` | `:disabled="!posForm.ticker"` + `x-show` errores inline |

### Estrategia de migración

1. **Extraer CSS primero** — No rompe funcionalidad. Los estilos se mueven a archivos `.css` y `index.html` los referencia con `<link>`. Verificar que la app se ve idéntica.
2. **Extraer JS** — Las funciones existentes se mueven a módulos `.js`. El HTML referencia los scripts con `<script src>`. Verificar que toda la funcionalidad existente sigue funcionando.
3. **Integrar Alpine.js** — Añadir el CDN de Alpine.js y migrar progresivamente: primero el tema, luego el drawer, luego los modales (posición y DCA), luego los toasts.
4. **Rediseñar el header** — Una vez que Alpine.js maneja el drawer, reemplazar el header actual con la versión minimalista.
5. **Rediseñar mobile.html** — Independiente de los cambios desktop. Refactor completo hacia keycards.
6. **Actualizar simuladores.html** — Solo link a `tokens.css` y eliminar duplicación.

## Infraestructura necesaria

No se requiere nueva infraestructura. La app sigue siendo estática servida por Spring Boot, sin nuevos servicios Docker.

## Tareas independientes

### Fase 1 — Extracción CSS (paralelizable)
- [ ] **T1.1** — Crear `css/tokens.css` extrayendo los design tokens de `index.html` (líneas 13-43, dark/light, tipografía, spacing). Añadir tipografía JetBrains Mono + DM Sans.
- [ ] **T1.2** — Crear `css/desktop.css` extrayendo todo el CSS de `index.html` (líneas 44-350 aprox: reset, layout, header, kpi, section, table, buttons, modals, news, alerts, log, toast, responsive).
- [ ] **T1.3** — Crear `css/mobile.css` extrayendo el CSS de `mobile.html` (líneas 14-145 aprox: mobile header, tab bar, pages, kpi, section, position card, etc.). Adaptar al nuevo diseño de keycards.

### Fase 2 — Extracción JS (paralelizable)
- [ ] **T2.1** — Crear `js/formatters.js` extrayendo `fmtEur`, `fmtPct`, `fmtShares`, `plClass`, `calcInvested`, `calcValue`, `calcPL`, `calcPLPct`, `totalInvested`, `totalValue`, `totalPL`.
- [ ] **T2.2** — Crear `js/api.js` extrayendo todos los fetch wrappers: `apiGet`, `apiCreate`, `apiUpdate`, `apiDelete`, APIs de DCA, watchlist, alerts, metrics, export, prices, etc.
- [ ] **T2.3** — Crear `js/charts.js` extrayendo funciones de Chart.js: `initHistoryChart`, `initPdHistChart`, `destroyChart`, `chartColors`, configs de sparklines, etc.

### Fase 3 — Alpine.js + refactor desktop
- [ ] **T3.1** — Crear `js/alpine-store.js` con el store Alpine completo (theme, drawer, modals, toasts, formularios, keyboard shortcuts). El store contiene toda la reactividad que antes se manejaba con variables globales y manipulación DOM.
- [ ] **T3.2** — Refactorizar `index.html`: eliminar `<style>` y `<script>` inline, enlazar CSS y JS externos, añadir `x-data="app()"` al `<html>`, adaptar el HTML a Alpine bindings.
- [ ] **T3.3** — Rediseñar el header de `index.html` a la versión minimalista (menú hamburguesa + logo + alertas + tema). Añadir nav drawer lateral con Alpine.js.
- [ ] **T3.4** — Convertir los modales (posición, DCA) a Alpine.js con `x-show`, `x-model`, vista previa reactiva, validación inline, ESC y click-outside.
- [ ] **T3.5** — Convertir el sistema de toasts a Alpine.js con `x-for` y transiciones CSS.

### Fase 4 — Mobile keycards (independiente de Fase 3)
- [ ] **T4.1** — Refactorizar `mobile.html`: nuevo header minimalista, summary card con sparkline, KPI chips scrollables, position cards con sparklines, watchlist compacta, tab bar. Usar `css/mobile.css` y Alpine.js CDN.
- [ ] **T4.2** — Implementar sparklines en posición cards (Chart.js mini charts sin ejes ni tooltips).

### Fase 5 — Simuladores + verificación
- [ ] **T5.1** — Actualizar `simuladores.html` para linkear `css/tokens.css` en vez de duplicar los design tokens. Mantener su CSS específico inline.
- [ ] **T5.2** — Verificación completa: arrancar la app, probar todas las funcionalidades (CRUD posiciones, DCA, watchlist, alertas, export, simuladores, tema claro/oscuro, mobile, drawer, modales, atajos de teclado).

## Rama

`feature/modernize-ui-ux`

## Archivos

| Acción | Archivo |
|--------|---------|
| CREAR | `src/main/resources/static/css/tokens.css` |
| CREAR | `src/main/resources/static/css/desktop.css` |
| CREAR | `src/main/resources/static/css/mobile.css` |
| CREAR | `src/main/resources/static/js/formatters.js` |
| CREAR | `src/main/resources/static/js/api.js` |
| CREAR | `src/main/resources/static/js/alpine-store.js` |
| CREAR | `src/main/resources/static/js/charts.js` |
| MODIFICAR | `src/main/resources/static/index.html` |
| MODIFICAR | `src/main/resources/static/mobile.html` |
| MODIFICAR | `src/main/resources/static/simuladores.html` |

## Verificación

1. `./mvnw spring-boot:run` — la app arranca sin errores
2. `http://localhost:19480/portfoliotracker/` — carga la página desktop
   - Header muestra solo: ☰ menú, logo, 🔔 alertas, ◐ tema
   - Click en ☰ abre nav drawer con todas las secciones
   - KPIs se cargan correctamente
   - Tabla de posiciones funciona
   - Gráficos Chart.js se renderizan
3. Probar modales:
   - Nueva Posición: formulario con vista previa reactiva, validación inline, guardar crea la posición
   - Nueva Operación: tabs Compra/Venta, vista previa con cálculo de nuevo precio medio, guardar registra
   - ESC cierra modales, click fuera cierra modales y drawer
4. `http://localhost:19480/portfoliotracker/mobile.html` — carga la versión mobile
   - Summary card con sparkline del portfolio
   - KPI chips scrollables horizontalmente
   - Position cards con icono, sparkline y stats
   - Watchlist compacta
   - Tab bar inferior funcional
5. `http://localhost:19480/portfoliotracker/simuladores.html` — carga sin errores de CSS
6. Tema claro/oscuro funciona en ambas versiones (desktop y mobile)
7. Atajos de teclado: ⌘K abre/cierra drawer, ⌘N abre modal de posición
