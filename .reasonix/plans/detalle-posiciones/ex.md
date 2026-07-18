# Research: detalle-posiciones

## Problem
Al abrir el modal de detalle de una posición activa, los campos operativos (peso objetivo, stop-loss, take-profit, notas, estrategia, nivel de riesgo, etc.) aparecen vacíos aunque los datos existen en base de datos. El servicio de exportación de informes sí los muestra, lo que confirma que la capa de persistencia funciona. La causa raíz es que el modal usa `fetch()` sin autenticación contra un endpoint protegido por Spring Security, y el error se silencia en un `.catch()` que inyecta un stub vacío.

## Stack & constraints
- **Backend:** Spring Boot 4.0.5, Java 21, JPA/Hibernate, PostgreSQL
- **Frontend:** Vanilla JS SPA (~440 KB monolítico en `index.html`), sin framework
- **Auth:** JWT (Supabase) validado por filtro custom `SecurityConfig.jwtAuthFilter()`. Toda ruta `/api/**` requiere autenticación.
- **API wrapper:** `authFetch()` en `api.js` añade header `Authorization: Bearer <token>`. `fetch()` nativo no.

## Affected surface
| File/Directory     | Role in the project | Relevance to this feature |
|-------------------|--------------------|---------------------------|
| `src/main/resources/static/index.html:5385` | `openDetailModal()` — carga detalle con `fetch()` | **BUG:** usa `fetch()` sin auth → 401 |
| `src/main/resources/static/index.html:7092-7096` | `loadAll()` — carga todos los detalles con `authFetch()` en `positionDetailsMap` | **Fuente de datos ya disponible** que el modal ignora |
| `src/main/resources/static/index.html:3299` | `let positionDetailsMap = {}` | Mapa en memoria con todos los detalles, poblado correctamente |
| `src/main/resources/static/index.html:5391-5393` | `.catch()` en `openDetailModal` | **Silencia el error** creando stub `{ticker, riskRating:'MEDIUM'}` |
| `src/main/resources/static/index.html:5781-5818` | `saveDetail()` — guarda con `authFetch()` (PUT) | **Sí funciona** — es asimétrico con la lectura |
| `src/main/resources/static/index.html:5411-5495` | `populateDetailModal(data)` | Renderiza todos los campos correctamente si recibe datos |
| `src/main/java/.../controller/PositionDetailController.java:30-36` | `GET /api/positions/{ticker}/detail` | Endpoint que el modal intenta llamar sin auth |
| `src/main/java/.../controller/PositionDetailController.java:24-27` | `GET /api/position-details` | Endpoint que `loadAll()` sí llama con auth |
| `src/main/java/.../config/SecurityConfig.java:55` | `.requestMatchers("/api/**").authenticated()` | Bloquea el `fetch()` sin token |
| `src/main/java/.../model/PositionDetail.java` | Entidad JPA con todos los campos operativos | Schema de datos confirmado: targetWeightPct, stopLoss, takeProfit, notes, etc. |

## Current flow
```
App init → requireAuth() → loadAll()
                              ├─ apiGet()           [authFetch ✓] → positions[]
                              ├─ apiDcaGetAll()     [authFetch ✓] → dcaEntries[]
                              ├─ GET /api/metrics   [authFetch ✓] → metrics
                              ├─ GET /api/position-details [authFetch ✓] → positionDetailsMap ✅
                              └─ renderAll() → tabla visible

Usuario hace clic en posición → openDetailModal(ticker)
  └─ fetch(GET /api/positions/{ticker}/detail)  [fetch ❌ SIN TOKEN]
       ├─ 401 Unauthorized → .catch()
       └─ .catch() → detailData = {ticker, riskRating:'MEDIUM'}  ← STUB VACÍO
            └─ populateDetailModal(stub) → campos vacíos ❌
```

**Fix:** `openDetailModal` debe leer de `positionDetailsMap[ticker]` que ya contiene los datos autenticados, eliminando el fetch duplicado.

## Reusable patterns
- `index.html:3714` — `saveDcaAllocation()` ya demuestra que `authFetch` contra `/api/positions/{ticker}/detail` funciona. Mismo endpoint, misma forma de acceder.
- `index.html:7092-7094` — `loadAll()` ya demuestra el patrón correcto: cargar todos los detalles con `authFetch` y almacenar en mapa indexado por ticker.
- `api.js:17-51` — `authFetch()` es el wrapper canónico para toda llamada autenticada. Cualquier uso de `fetch()` desnudo contra `/api/**` es un bug.

## Questions / Decisions
| # | Phase    | Question | Answer | Impact |
|---|----------|----------|--------|--------|
| 1 | Goal     | ¿Qué necesitas que pase cuando esté arreglado? | Ver y editar los detalles in-situ | Alcance: lectura + guardado del modal |
| 2 | Goal     | ¿Es consistente o intermitente? | Consistente: nunca se ven en ninguna posición | Descartamos race conditions o datos corruptos |
| 3 | Goal     | ¿Los datos existen en BD? | Sí, los informes exportados sí los muestran | El bug está en la lectura del frontend, no en la capa de datos |
| 4 | Technical| ¿Usar positionDetailsMap ya en memoria o fix mínimo? | Usar positionDetailsMap | Elimina el fetch duplicado, más rápido y sin dependencia de red extra |
| 5 | Technical| ¿Hay ventana de carrera? | No, loadAll() → renderAll() se completa antes de que el usuario pueda hacer clic | Seguro usar positionDetailsMap directamente |

## Research Contract
| Certainty | Evidence (file:line) |
|-----------|---------------------|
| `openDetailModal` usa `fetch()` sin auth | `index.html:5385` — `fetch(API_DETAIL(ticker))` |
| `SecurityConfig` bloquea `/api/**` sin token | `SecurityConfig.java:55` — `.requestMatchers("/api/**").authenticated()` |
| `loadAll()` ya carga los detalles correctamente con auth | `index.html:7093` — `authFetch(`${BASE}/api/position-details`)` |
| `saveDetail()` usa `authFetch` correctamente | `index.html:5799` — `authFetch(API_DETAIL(detailTicker), {method:'PUT'...})` |
| `saveDcaAllocation` confirma que el endpoint funciona con auth | `index.html:3714` — mismo endpoint, mismo authFetch |
| `populateDetailModal` consume los campos correctos | `index.html:5460-5495` — targetWeightPct, stopLoss, takeProfit, notes, strategy, etc. |
| `positionDetailsMap` tiene el mismo schema que el endpoint individual | `PositionDetail.java:19-59` — misma entidad devuelta por ambos endpoints |
| El `.catch()` silencia el error sin feedback al usuario | `index.html:5391-5393` — stub silencioso, sin toast ni log |
| ExportService confirma que los datos existen en BD | `ExportService.java:67-69` — `positionDetailService.findAll()` poblado |
