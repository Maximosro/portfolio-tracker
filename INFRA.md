# 🏗️ Infraestructura y CI/CD — Portfolio Tracker

## Arquitectura

```
┌─ Usuario ───────────────────────────────────────────────┐
│  https://portfolio.srv1158554.hstgr.cloud                │
│  HTML5 + Alpine.js + Chart.js (SPA sin build)            │
│  Auth: @supabase/supabase-js (JWT ES256)                 │
└───────────────────┬──────────────────────────────────────┘
                    │ HTTPS (TLS 1.3)
                    ▼
┌─ Hostinger KVM 1 — srv1158554.hstgr.cloud ──────────────┐
│  IP: 82.112.240.102 | 4 GB RAM | 1 vCPU | 50 GB SSD     │
│  SO: Ubuntu 24.04 | Docker 29+                           │
│  ┌──────────────────────────────────────────────────┐    │
│  │ Traefik (reverse proxy + Let's Encrypt)           │    │
│  │  :80 → :443 | TLS auto-renewal                   │    │
│  │  routes:                                          │    │
│  │    portfolio.srv... → http://172.18.0.1:19480    │    │
│  │    n8n.srv... → :5678                             │    │
│  └──────────────────────────────────────────────────┘    │
│  ┌─────────────┐  ┌──────────────────────────────────┐   │
│  │ n8n (:5678) │  │ portfolio-tracker (:19480)        │   │
│  │ workflow    │  │ Spring Boot 4.0.5 / Java 21       │   │
│  │ automation  │  │ network_mode: host                │   │
│  │             │  │ healthcheck: /portfoliotracker/   │   │
│  └─────────────┘  └──────────────┬───────────────────┘   │
└──────────────────────────────────┼───────────────────────┘
                                   │ JDBC SSL (:6543)
                                   ▼
┌─ Supabase Cloud ─────────────────────────────────────────┐
│  PostgreSQL 17 | Auth (JWT ES256) | RLS                  │
│  db.agtkcnxmlbccbwmsuxdz.supabase.co                     │
│  Connection pooling: port 6543 (SSL)                     │
└──────────────────────────────────────────────────────────┘
```

## Servicios y proveedores

| Servicio | Plan | Uso |
|----------|------|-----|
| **Hostinger KVM 1** | VPS Linux | Despliegue Docker (portfolio-tracker, n8n, Traefik) |
| **Supabase** | Cloud | PostgreSQL 17 + Auth (JWT) |
| **GitHub Container Registry** | `ghcr.io/maximosro/portfolio-tracker` | Almacén de imágenes Docker |
| **GitHub Actions** | CI/CD | Build, test, push y despliegue automático |
| **Yahoo Finance** | Gratuito (API no oficial) | Precios de activos en tiempo real |
| **open.er-api.com** | Gratuito | Tipos de cambio EUR |
| **Telegram Bot API** | Gratuito | Notificaciones y alertas (opcional) |

## Variables de entorno

### VPS — `/root/docker-compose.yml`

| Variable | Descripción |
|----------|-------------|
| `SUPABASE_DB_PASSWORD` | Password de la BD Supabase PostgreSQL |
| `SPRING_DEVTOOLS_RESTART_ENABLED` | `false` en producción (deshabilita hot-reload) |
| `TZ` | `Europe/Madrid` — zona horaria |
| `JAVA_OPTS` | `-Xmx512m -Xms256m -XX:+UseG1GC` |
| `DOMAIN_NAME` | `srv1158554.hstgr.cloud` |
| `SUBDOMAIN` | `n8n` |
| `GENERIC_TIMEZONE` | `Europe/Berlin` |
| `SSL_EMAIL` | Email para notificaciones Let's Encrypt |

### GitHub Secrets

| Secret | Uso |
|--------|-----|
| `VPS_HOST` | IP de la VPS (`82.112.240.102`) |
| `VPS_USER` | Usuario SSH (`root`) |
| `VPS_SSH_PRIVATE_KEY` | Clave privada ed25519 para autenticación SSH |
| `GHCR_PAT` | Personal Access Token con scope `write:packages` para push a GHCR |

### Desarrollo local

| Variable | Entorno | Descripción |
|----------|---------|-------------|
| `SUPABASE_DB_PASSWORD` | todos | Password BD Supabase |
| `SPRING_PROFILES_ACTIVE` | opcional | `h2` para desarrollo offline sin auth |

## GitHub Actions

### PR Tests (`pr-test.yml`)

```yaml
Trigger: pull_request → main, dev
```

- Se ejecuta en cada PR abierta/actualizada hacia `main` o `dev`
- Solo ejecuta `./mvnw test -B` (tests JUnit con H2 en memoria)
- No construye Docker ni despliega
- Tiempo: ~25 segundos

### Deploy to Hostinger KVM (`docker-build-deploy.yml`)

```yaml
Trigger: push → main | workflow_dispatch
```

| Paso | Descripción |
|------|-------------|
| 1. Checkout | Clona el repositorio |
| 2. JDK 21 | Configura Java 21 Temurin con cache Maven |
| 3. Maven wrapper | `chmod +x mvnw` |
| 4. Run tests | `./mvnw test -B` (43 tests) |
| 5. Login GHCR | Autentica con `GHCR_PAT` |
| 6. Docker metadata | Genera tags: `latest` + `sha-<commit>` |
| 7. Build & Push | Docker multi-stage → push a GHCR |
| 8. Deploy SSH | `ssh root@VPS` → `docker compose pull && up -d` |
| 9. Smoke test | Curl con reintentos (12×5s) a la URL de producción |

Tiempo total: ~5 minutos.

## Despliegue manual (backup)

Si GitHub Actions falla:

```bash
# Build local
docker build -t ghcr.io/maximosro/portfolio-tracker:latest .

# Push a GHCR
echo "$GITHUB_TOKEN" | docker login ghcr.io -u Maximosro --password-stdin
docker push ghcr.io/maximosro/portfolio-tracker:latest

# Deploy en VPS
ssh root@82.112.240.102
cd /root
docker compose pull portfolio-tracker
docker compose up -d portfolio-tracker
```

## Rollback

La imagen anterior siempre está disponible vía tag `sha-<commit>`:

```bash
ssh root@82.112.240.102 "
  cd /root
  docker compose pull portfolio-tracker  # latest vuelve a la actual
  # O para una versión específica:
  docker pull ghcr.io/maximosro/portfolio-tracker:sha-abc1234
  # Editar /root/docker-compose.yml y cambiar el tag, luego:
  docker compose up -d portfolio-tracker
"
```

## Docker Compose (VPS)

Fichero: `/root/docker-compose.yml` (proyecto `root`)

3 servicios:
- **traefik**: Reverse proxy, TLS Let's Encrypt, puertos 80/443
- **n8n**: Automatización, solo escucha en `127.0.0.1:5678`, rutas Traefik
- **portfolio-tracker**: App Spring Boot, `network_mode: host`, puerto `19480`

Volúmenes externos: `traefik_data`, `n8n_data`

## SSH — Acceso a la VPS

- **Host**: `82.112.240.102` (srv1158554.hstgr.cloud)
- **Usuario**: `root`
- **Clave CI/CD**: `id_ed25519` (`rothar@portfolio`), registrada en hPanel → VPS → SSH Keys
- **Clave local**: `~/.ssh/id_ed25519`

⚠️ La clave SSH en GitHub Secrets **debe usar saltos de línea Unix (\n)**. Si se sube desde Windows PowerShell:

```powershell
$key = (Get-Content ~/.ssh/id_ed25519 -Raw) -replace "`r`n", "`n"
$key | gh secret set VPS_SSH_PRIVATE_KEY --repo Maximosro/portfolio-tracker
```

## GHCR — Container Registry

- **URL**: `ghcr.io/maximosro/portfolio-tracker`
- **Visibilidad**: Privada
- **Autenticación CI**: `GHCR_PAT` (Personal Access Token con `write:packages`)
- **Autenticación VPS**: Login manual (`docker login ghcr.io`)
- **Tags**: `latest` + `sha-<7-char-commit>`

## Supabase

- **URL**: https://supabase.com/dashboard/project/agtkcnxmlbccbwmsuxdz
- **DB**: PostgreSQL 17, conexión JDBC SSL puerto `6543`
- **Auth**: Email + password, JWT ES256, validación vía JWKS
- **Tablas**: `positions`, `dca_history`, `position_details`, `price_history`, `portfolio_snapshots`, `watchlist`, `watchlist_alert`, `market_schedules`, `investment_plan`, `planned_cash_flows`, `app_settings`, `telegram_channel_messages`, `position_alerts`
- **Hibernate**: `ddl-auto: validate` (prod) / `create-drop` (test)

## Flujo de desarrollo

```
feature/* → PR a dev → PR Tests ✅ → merge a dev → PR a main → PR Tests ✅ → merge a main → Deploy 🚀
```

- **Ramas**: `dev` (desarrollo), `main` (producción)
- **PR a dev**: solo tests
- **PR a main**: solo tests
- **Merge a main**: tests + build + push + deploy + smoke

## Comandos útiles

```bash
# Tests locales
./mvnw test

# Build local (sin tests)
./mvnw clean verify -DskipTests

# Ver estado VPS
curl -s https://portfolio.srv1158554.hstgr.cloud/portfoliotracker/

# Ver workflows
gh run list --repo Maximosro/portfolio-tracker --limit 5

# Deploy manual vía workflow
gh workflow run "Deploy to Hostinger KVM" --repo Maximosro/portfolio-tracker --ref main
```
