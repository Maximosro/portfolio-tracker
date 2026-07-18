# Plan: detalle-posiciones

## Iteration history
| Version | Changes | Trigger |
|---------|---------|---------|
| v1 FINAL | Plan inicial — fix en `openDetailModal` para usar `positionDetailsMap` | — |

## Functional overview
### What
Hacer que el modal de detalle de una posición (index.html) muestre los datos operativos reales (peso objetivo, stop-loss, take-profit, notas, estrategia, riesgo) que ya existen en base de datos y en memoria.

### Why
El usuario necesita ver y editar esos detalles in-situ desde la UI. Actualmente el modal siempre aparece vacío porque la llamada HTTP que los carga usa `fetch()` sin token JWT → 401 → el `.catch()` inyecta un stub vacío sin informar del error.

### What it improves / fixes
| Antes | Después |
|-------|---------|
| Modal abre con todos los campos vacíos (stop-loss, take-profit, notas, peso objetivo, etc.) | Modal muestra los datos reales cargados desde `positionDetailsMap` |
| `fetch()` sin auth → 401 silencioso | Sin llamada HTTP: lectura directa del mapa en memoria |
| Usuario no sabe si hay datos o no | Si no hay detalle para ese ticker, se muestra stub con `riskRating: 'MEDIUM'` (mismo default que el backend) |
| `saveDetail()` sí funciona (usa `authFetch`) | Sin cambios — guardar ya funciona |

## Technical design
### Input/output contract
**No hay cambios en la API.** El contrato del modal sigue siendo el mismo:

```
openDetailModal(ticker: string) → void
  - Lee positionDetailsMap[ticker]
  - Si no existe → stub { ticker, riskRating: 'MEDIUM' }
  - populateDetailModal(data) → renderiza todos los campos
```

`populateDetailModal(data)` ya espera un objeto con los campos de `PositionDetail`:
```
{
  targetWeightPct: number | null,
  riskRating: 'LOW' | 'MEDIUM' | 'HIGH',
  strategy: string | null,
  stopLoss: number | null,
  takeProfit: number | null,
  trailingStopPct: number | null,
  dcaTargetPrice: number | null,
  alertPriceAbove: number | null,
  alertPriceBelow: number | null,
  notes: string | null,
  updatedAt: string (ISO) | null
}
```

### Data design
Sin cambios. No se crean ni modifican tablas, entidades, o DTOs.

### Execution flow
```
Usuario hace clic en posición → openDetailModal(ticker)
  │
  ├─ detailPosition = positions.find(p => p.ticker === ticker)
  ├─ Set header (ticker, name, dot color)
  ├─ switchDetailTab('overview')
  │
  ├─ detailData = positionDetailsMap[ticker]          ← NUEVO: lectura directa
  │   └─ || { ticker, riskRating: 'MEDIUM' }          ← fallback si no existe
  │
  ├─ populateDetailModal(detailData)                  ← sin cambios
  │   ├─ Renderiza stats grid (precio, P&L, TIR…)
  │   ├─ Rellena peso objetivo, riesgo, estrategia
  │   ├─ Rellena límites (stop-loss, take-profit, etc.)
  │   ├─ Rellena notas
  │   └─ Muestra updatedAt si existe
  │
  └─ document.getElementById('detailOverlay').classList.add('open')
```

**Garantía de disponibilidad:** `loadAll()` (línea 7100) ejecuta `renderAll()` solo después de poblar `positionDetailsMap` (líneas 7092-7096). El usuario no puede hacer clic en una posición antes de que la tabla esté renderizada. No hay race condition.

### Integration with existing code

| Punto de integración | Archivo:Línea | Qué cambia |
|---------------------|---------------|------------|
| `openDetailModal()` | `index.html:5384-5394` | Se reemplaza el bloque `fetch().then().catch()` por lectura de `positionDetailsMap` |
| `positionDetailsMap` | `index.html:3299` | Ya existe, poblado en `loadAll():7092-7096` con `authFetch` |
| `API_DETAIL` | `index.html:5365` | **Se mantiene** — `saveDetail():5799` aún lo usa con `authFetch` |
| `populateDetailModal()` | `index.html:5411` | Sin cambios |
| `saveDetail()` | `index.html:5781` | Sin cambios — ya usa `authFetch` correctamente |
| `detailData` | `index.html:5368` | Sin cambios — sigue siendo la variable que alimenta el modal y la UI |

### Configuration & deployment
Sin cambios. No se tocan variables de entorno, configs, migraciones, ni Docker.

## Tasks
### T1 — Fix openDetailModal para usar positionDetailsMap
- **Files**: `src/main/resources/static/index.html`
- **Change**: Reemplazar el bloque de líneas 5384-5394:
  ```javascript
  // Load detail from server
  fetch(API_DETAIL(ticker))
    .then(r => r.json())
    .then(data => {
      detailData = data;
      populateDetailModal(data);
    })
    .catch(() => {
      detailData = { ticker, riskRating: 'MEDIUM' };
      populateDetailModal(detailData);
    });
  ```
  por:
  ```javascript
  // Load detail from positionDetailsMap (preloaded by loadAll with auth)
  detailData = positionDetailsMap[ticker] || { ticker, riskRating: 'MEDIUM' };
  populateDetailModal(detailData);
  ```
  **Exact anchors:**
  - `old_string`: el bloque completo de 11 líneas desde `  // Load detail from server` hasta `    });`
  - `new_string`: las 3 líneas nuevas
- **Verification**: 
  1. `grep -n "fetch(API_DETAIL" src/main/resources/static/index.html` → sin resultados (el fetch buggy desapareció)
  2. `grep -n "positionDetailsMap\[ticker\]" src/main/resources/static/index.html` → debe aparecer la nueva línea en `openDetailModal`
  3. Arrancar la app con `./mvnw spring-boot:run`, hacer login, hacer clic en una posición con datos en `position_details` → el modal debe mostrar stop-loss, take-profit, notas, peso objetivo, etc.
- **Depends on**: —

## Branch
`fix/detalle-posiciones`

## Risks (pre-mortem)
| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| `positionDetailsMap[ticker]` es `undefined` para un ticker recién creado | Baja | Baja — mismo comportamiento que el stub actual (`riskRating: 'MEDIUM'`, campos vacíos) | El fallback `\|\| { ticker, riskRating: 'MEDIUM' }` lo cubre |
| `saveDetail()` deja de funcionar porque se eliminó `API_DETAIL` | Nula | Alta | `API_DETAIL` se mantiene — solo se elimina el `fetch()` que lo usaba |
| `loadAll()` falla y `positionDetailsMap` queda vacío | Baja | Media — el modal mostraría stub para todas las posiciones | `loadAll()` ya tiene su propio manejo de errores (línea 7096: `.catch()` → `{}`). Si falla, la tabla tampoco cargaría, así que el usuario no llegaría a abrir el modal |

## FACTS Score
F: 5/5  A: 5/5  C: 5/5  T: 5/5  S: 5/5  Mean: 5.00

- **Feasible:** Cambio de 3 líneas en 1 archivo, sin dependencias externas, sin nuevas librerías.
- **Atomic:** Una sola tarea, una sola función, una sola responsabilidad.
- **Clear:** Orden trivial (1 tarea sin dependencias). Los anchors de edición son exactos.
- **Testable:** Verificación con grep + test manual de integración.
- **Size:** Cambio mínimo — 11 líneas fuera, 3 líneas dentro. No toca backend, no toca DB, no toca deploy.

## Verdict: 🟢 GO
| Axis | Status |
|------|--------|
| Problem clarity | ✅ Causa raíz confirmada con evidencia file:line |
| Technical compatibility | ✅ Sin cambios en API, DB, o dependencias |
| External dependencies | ✅ Ninguna — todo es frontend estático |
| Mitigated risks | ✅ 3 riesgos identificados, todos con mitigación |
| Testable acceptance criteria | ✅ grep + test manual |
