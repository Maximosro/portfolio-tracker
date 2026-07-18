# Implement: detalle-posiciones

## Branch
`fix/detalle-posiciones` (local) — pendiente de push a `origin`

## Task tracking
| Task | Status | Verification | Commit |
|------|--------|-------------|--------|
| T1 — Fix openDetailModal para usar positionDetailsMap | ✅ | grep checks pasan + build limpio + auto-review pasado | `c3a39fa` |

## Changes made
| File | Action | Description |
|------|--------|-------------|
| `src/main/resources/static/index.html` | MODIFY | Reemplazado `fetch(API_DETAIL(ticker))` sin auth (11 líneas) por lectura directa de `positionDetailsMap[ticker]` (3 líneas) en `openDetailModal()` |

## PR
**No creado automáticamente** — el entorno no tiene credenciales de escritura contra GitHub (ni git CLI, ni MCP tools, ni API REST aceptan los tokens disponibles).

### Push manual requerido
```bash
cd /home/sergio/Workspace/portfolio-tracker
git push origin fix/detalle-posiciones
# Si pide auth, usar un PAT con scope repo:
# git push https://TOKEN@github.com/Maximosro/portfolio-tracker.git fix/detalle-posiciones
```

Luego crear PR desde `fix/detalle-posiciones` → `dev` con este cuerpo:

```
## What
Fix para que el modal de detalle de posición muestre los datos operativos reales
(peso objetivo, stop-loss, take-profit, notas, estrategia, riesgo).

## Root cause
openDetailModal() usaba fetch() sin token JWT contra /api/positions/{ticker}/detail.
Spring Security devolvía 401 y el .catch() inyectaba un stub vacío sin avisar.

## Fix
Se reemplaza la llamada fetch() por lectura directa de positionDetailsMap[ticker],
que ya está poblado por loadAll() con authFetch. Mismos datos, sin HTTP extra.

## Tasks
- [x] T1 — Fix openDetailModal para usar positionDetailsMap

## Risks mitigated
- ticker sin entrada → fallback `|| { ticker, riskRating: 'MEDIUM' }`
- API_DETAIL no se eliminó → saveDetail() sigue funcionando
- loadAll() failure → positionDetailsMap se inicializa como {} seguro

## Plan
https://github.com/Maximosro/portfolio-tracker/blob/dev/.reasonix/plans/detalle-posiciones/pl.md
```

## Auto-review result
✅ **Ship as-is.** La revisión confirma:
- `fetch(API_DETAIL)` eliminado (grep vacío)
- `positionDetailsMap[ticker]` presente en `openDetailModal` (línea 5385)
- `saveDetail()` intacto — `API_DETAIL` y `authFetch` preservados
- Todos los riesgos del pre-mortem mitigados
- `populateDetailModal` es resiliente a datos parciales (`??` y `||`)
- Sin cambios fuera de scope

## Rollback
```bash
git revert c3a39fa
# O restaurar las 11 líneas originales en openDetailModal():
#   fetch(API_DETAIL(ticker))
#     .then(r => r.json())
#     .then(data => { detailData = data; populateDetailModal(data); })
#     .catch(() => { detailData = { ticker, riskRating: 'MEDIUM' }; populateDetailModal(detailData); });
```

## Notes
- **Bloqueo de push:** Los tokens `GH_TOKEN` y `GITHUB_TOKEN` del entorno no tienen permisos de escritura o están expirados. Se intentaron 4 métodos (git CLI con password, git CLI con token como username, GitHub MCP `push_files`, GitHub REST API). Todos fallaron con auth errors.
- El commit está hecho localmente en `fix/detalle-posiciones` con hash `c3a39fa`.
- Build (`./mvnw package -DskipTests`) pasa limpio.
- `mobile.html` verificado: no tiene el bug (usa `authFetch` en línea 1097).
