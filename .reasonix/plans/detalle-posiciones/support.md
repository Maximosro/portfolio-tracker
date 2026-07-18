# Support: detalle-posiciones

## Unanswered questions
- _(ninguna pendiente)_

## Unverified items
- `index.html:3682` — `positionDetailsMap` se usa en la columna DCA de la tabla. No verificamos si ese flujo tiene el mismo bug de `fetch()` sin auth, pero usa `positionDetailsMap` que ya viene de `loadAll()`, así que debería funcionar.
- ✅ `mobile.html` verificado — línea 1097 usa `authFetch` correctamente. No tiene el bug.

## Decisions made
- **Decision:** Usar `positionDetailsMap[ticker]` en `openDetailModal` en vez de hacer fetch individual.
  - **Alternatives:** (a) cambiar `fetch` → `authFetch` manteniendo la llamada individual.
  - **Rationale:** `positionDetailsMap` ya está en memoria, poblado con auth, reduce latencia, elimina código duplicado, y evita el problema de raíz. La alternativa (a) arreglaría el bug pero dejaría una llamada HTTP redundante.
- **Decision:** Mantener `saveDetail()` con su `authFetch` actual (PUT) — ya funciona.
- **Decision:** Mantener `API_DETAIL` — `saveDetail()` lo usa en línea 5799.
- **Decision:** Mantener fallback `{ ticker, riskRating: 'MEDIUM' }` cuando `positionDetailsMap[ticker]` no exista — mismo comportamiento que el stub actual y que el default del backend.

## Plan iteration log
| Version | Changes | Trigger |
|---------|---------|---------|
| v1 FINAL | Plan inicial — 1 tarea, 1 archivo | Diseño directo desde ex.md |
| Scope check | Descartado mobile.html (no tiene el bug) | ask(): ¿incluir mobile.html? → verificado, usa authFetch |
