# 📊 Portfolio Tracker

Aplicación de escritorio/web **100% local y gratuita** para seguimiento de inversiones con estrategia **Dollar Cost Averaging (DCA)**.  
Gestiona posiciones, historial de compras/ventas, alertas operativas, watchlist con alertas, simuladores financieros, notificaciones Telegram y genera informes optimizados para análisis con IA.

> **Tus datos nunca salen de tu ordenador.** Sin registro, sin cloud, sin suscripciones, sin telemetría.

---

## Stack

- **Backend:** Spring Boot 4.0.5 / Java 21
- **Base de datos:** SQLite (`data/portfolio.db`) — zero config
- **Frontend:** HTML + JavaScript vanilla (Chart.js) — sin frameworks pesados
- **Precios:** Yahoo Finance (conversión automática a EUR)
- **Noticias:** Google News (scraping RSS)
- **Notificaciones:** Telegram Bot API (opcional)
- **Tipos de cambio:** open.er-api.com (caché de 6h)

## Requisitos

- Java 21+

## 🚀 Arranque rápido

### Desarrollo

```bash
./mvnw spring-boot:run
```

### Producción (JAR ejecutable)

```bash
./mvnw clean package -DskipTests
java -jar target/PortfolioTracker.jar
```

### 🌐 Acceso web

```
http://localhost:19480/portfoliotracker/
```

> Puerto: `19480` — Context path: `/portfoliotracker`  
> La app escucha en `0.0.0.0`, accesible desde otros dispositivos de la red local.

---

## 📦 Distribución portable

Al ejecutar `mvn package` se genera un **zip autocontenido** en `target/PortfolioTracker-dist.zip`:

```
PortfolioTracker/
├── PortfolioTracker.jar         ← JAR ejecutable (app + web + seeds)
├── start.bat                    ← Arranque manual (Windows)
├── start.sh                     ← Arranque manual (Linux/Mac)
├── install-service.bat          ← Instalar como servicio Windows (auto-arranque)
├── uninstall-service.bat        ← Desinstalar servicio Windows
└── data/
    ├── portfolio.db             ← Base de datos con tus datos
    ├── seed.sql                 ← Datos semilla (posiciones iniciales)
    └── price_history_seed.sql   ← Precios semilla
```

### Instalar como servicio en Windows 11

1. Descomprimir `PortfolioTracker-dist.zip`
2. Clic derecho en **`install-service.bat`** → **Ejecutar como administrador**
3. El servicio arrancará automáticamente cada vez que inicies sesión en Windows
4. Acceder en: **http://localhost:19480/portfoliotracker/**
5. Para desinstalar: ejecutar `uninstall-service.bat` como administrador

### Uso manual

- **Windows:** doble clic en `start.bat` (abre el navegador automáticamente)
- **Linux/Mac:** `./start.sh`

---

## ✨ Funcionalidades principales

### 📈 Dashboard y posiciones
- Panel de resumen con KPIs: capital invertido, valor actual, P&L (no realizado + realizado), XIRR, inversión del mes
- Tabla de posiciones con precio actual, variación diaria, P&L por posición y TIR individual
- Gráfico de distribución de cartera (tarta) con pesos por posición
- Gráfico de evolución de precios por ticker con rangos seleccionables (1D, 1S, 1M, 3M, 6M, 1A, YTD, Todo)
- **Posiciones cerradas**: cuando vendes todas las participaciones, la posición se mueve a una sección dedicada con P&L realizado, periodo de tenencia y TIR

### 🔄 Gestión DCA completa (compras y ventas)
- Registro de operaciones de **compra (BUY)** y **venta (SELL)**
- Al registrar cualquier operación, se **recalculan automáticamente** `shares` y `avgPrice` de la posición
- **P&L realizado** por cada venta: `(precio venta − precio medio) × acciones vendidas`
- Vista previa en tiempo real del nuevo precio medio antes de confirmar
- Paginación con "Ver más" para historiales largos
- **Importación masiva** de operaciones desde archivo JSON (crea posiciones automáticamente si no existen)

### ⚙️ Detalle operativo por posición
Cada posición tiene un panel con pestañas:
- **Resumen:** Peso en cartera, estadísticas, nivel de riesgo, estrategia
- **Límites:** Stop-loss, take-profit, trailing stop, DCA target, alertas de precio, con indicadores de distancia
- **Notas:** Espacio libre para análisis personal (hasta 2.000 caracteres)
- **🤖 IA:** Generador de prompts para Claude/ChatGPT con importación automática de respuestas JSON

### 💰 Precios en tiempo real
- Actualización automática **cada 10 minutos** vía Yahoo Finance (ciclo ligero)
- Cada 3 ciclos (30 min) se hace **fetch extendido** con datos de volumen
- Conversión automática a EUR usando tasas de `open.er-api.com` (caché de 6 horas)
- Divisas soportadas: EUR, USD, GBP, GBp/GBX, CHF, SEK y cualquier otra devuelta por la API
- Cada precio se guarda en `price_history` para gráficos y consultas históricas
- **Snapshot de cierre** a medianoche: guarda `previousClose` para calcular variación diaria
- **Control de horarios de mercado:** solo consulta precios cuando el mercado está abierto (configurable)
- **Ventana post-cierre** de 10 minutos con reintentos automáticos para capturar el precio de cierre real

### 🔔 Sistema de alertas
- **Stop-Loss / Take-Profit**: alerta cuando el precio alcanza los límites configurados
- **Trailing Stop**: alerta basada en porcentaje de caída desde precio medio
- **DCA Target**: notifica cuando el precio baja al nivel objetivo de compra
- **Alerta Precio Superior/Inferior**: umbrales personalizados por posición
- **Peso objetivo**: alerta si la ponderación se desvía ≥5pp del target configurado
- Severidades: 🔴 CRÍTICA, 🟡 AVISO, 🔵 INFO
- Las alertas leídas se **persisten en localStorage** (no reaparecen al recargar)

### 📊 Métricas y rentabilidad
- **XIRR (TIR anualizada):** calculado con flujos DCA reales. Global y por posición
- **P&L realizado total:** suma de todas las ventas ejecutadas
- **Rentabilidad por periodos:** hoy, semana, mes, trimestre, YTD, 1 año — ajustada por DCA
- **Detalle de cartera:** pantalla completa con equity curve, distribución, historial por periodo

### 📉 Evolución patrimonial (Snapshots)
- **Snapshot diario** automático a las 23:55
- Rangos: 1M, 3M, 6M, 1A, YTD, Todo
- Retornos calculados como **cambio en P&L** para descontar aportaciones DCA

### 👁️ Watchlist con alertas
- Lista de seguimiento para activos no comprados
- Variación diaria, semanal y mensual + volumen
- **Alertas configurables**: PRICE_ABOVE, PRICE_BELOW, VOLUME_ABOVE, VOLUME_BELOW
- Cooldown de 24h + notificación por Telegram

### 🧮 Simuladores financieros
5 simuladores integrados:
- **Proyección de cartera:** valor futuro con aportaciones + rendimiento
- **Simulador hipotecario:** cuota, amortización, intereses
- **Simulador de retiradas (FIRE):** retirada mensual y duración del capital
- **Interés compuesto:** crecimiento con aportaciones y reinversión
- **Monte Carlo:** simulación probabilística con percentiles

### 📰 Noticias
- Google News para cada posición (por ticker, nombre y sector)
- Filtrado automático de noticias financieras relevantes

### 📄 Exportación / Informes para IA
- Informe Markdown completo optimizado para LLMs
- Incluye: resumen ejecutivo, detalle por posición, historial DCA, distribución, riesgo (HHI), plan de inversión

### ✈️ Integración con Telegram (opcional)
- **Alertas en tiempo real:** envía alertas DANGER/WARNING tras cada actualización de precios
- **Resumen diario:** L-V a las 18:00 con valor total, P&L, top movers, TIR, alertas
- **Alertas de watchlist:** notificación cuando se disparan
- **Configuración en caliente** desde la app (sin reiniciar)
- **Canal @ultimominutoOTC:** polling de mensajes
- Persistencia en BD, cooldown de 24h

### 🎯 Plan de inversión
- Presupuesto mensual con barra de progreso en el dashboard
- Flujos de caja planificados (extraordinarios/recurrentes) con estado pendiente/ejecutado

### 🕐 Horarios de mercado
- Configurable por sufijo de ticker (`.DE`, `.L`, `.MC`, etc.)
- Solo consulta precios con mercado abierto + ventana post-cierre
- Reintentos automáticos para capturar precio de cierre

### 📋 Registro de actividad
- Ring buffer de 2.000 entradas en memoria
- Filtrable por categoría: Precios, Telegram, Watchlist, DCA, Snapshots, Alertas, Sistema

### 🧹 Compactación de datos
- Automática cada noche a las 02:00
- Retención escalonada: hoy (todos), semana (1/hora), mes (1/día), año (1/semana)

### 📱 Versión móvil
- Detección automática de User-Agent → redirección a vista optimizada
- Bypass con `?desktop=true`

### 🌙 Tema claro / oscuro
- Toggle en cabecera, preferencia persistida en localStorage

---

## Base de datos

- Archivo SQLite en `data/portfolio.db` (ruta relativa al directorio de ejecución)
- Se crea automáticamente al arrancar si no existe
- Si la BD está vacía, se ejecutan los seeds (busca primero en `./data/`, luego dentro del JAR)
- `ddl-auto: update` — Hibernate gestiona el esquema automáticamente
- Tablas: `positions`, `dca_history`, `position_details`, `price_history`, `portfolio_snapshots`, `watchlist`, `watchlist_alert`, `market_schedules`, `investment_plan`, `planned_cash_flows`, `app_settings`, `telegram_channel_messages`

---

## API REST

Todos los endpoints están bajo el context path `/portfoliotracker`.

### Posiciones

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/positions` | Listar todas las posiciones |
| `GET` | `/api/positions/{ticker}` | Obtener una posición |
| `POST` | `/api/positions` | Crear posición |
| `PUT` | `/api/positions/{ticker}` | Actualizar posición |
| `DELETE` | `/api/positions/{ticker}` | Eliminar posición (cascada: borra DCA, precios y detalle operativo) |

### DCA (Dollar Cost Averaging)

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/dca` | Listar historial DCA completo |
| `GET` | `/api/dca/{ticker}` | Historial DCA de un ticker |
| `POST` | `/api/dca` | Registrar compra/venta DCA (recalcula posición) |
| `PUT` | `/api/dca/{id}` | Modificar entrada DCA (recalcula posición) |
| `DELETE` | `/api/dca/{id}` | Eliminar entrada DCA (recalcula posición) |

### Detalle operativo por posición

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/position-details` | Listar todos los detalles operativos |
| `GET` | `/api/positions/{ticker}/detail` | Obtener detalle de una posición |
| `PUT` | `/api/positions/{ticker}/detail` | Guardar/actualizar detalle |

### Precios

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/prices/refresh` | Forzar actualización de precios |
| `GET` | `/api/prices/last-update` | Timestamp de última actualización |
| `GET` | `/api/prices/history/{ticker}?range=1m` | Histórico de precios de un ticker |
| `GET` | `/api/prices/history?range=1m` | Histórico global |
| `GET` | `/api/prices/history/stats` | Estadísticas del histórico |
| `POST` | `/api/prices/history/purge` | Compactación manual |

Rangos válidos: `1d`, `1w`, `1m`, `3m`, `6m`, `1y`, `ytd`, `all`

### Métricas

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/metrics` | XIRR global y por posición, P&L realizado |
| `GET` | `/api/metrics/returns` | Rentabilidad por periodos |

### Snapshots

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/snapshots?range=3m` | Snapshots del portfolio |
| `GET` | `/api/snapshots/period-history?period=week` | Histórico por periodo |

### Alertas

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/alerts` | Alertas activas |

### Watchlist

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/watchlist` | Listar watchlist |
| `POST` | `/api/watchlist` | Añadir item |
| `PUT` | `/api/watchlist/{id}` | Actualizar item |
| `DELETE` | `/api/watchlist/{id}` | Eliminar item |
| `POST` | `/api/watchlist/refresh` | Actualizar precios |

### Alertas de Watchlist

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/watchlist/{id}/alerts` | Listar alertas de un item |
| `POST` | `/api/watchlist/{id}/alerts` | Crear alerta |
| `PUT` | `/api/watchlist/{id}/alerts/{alertId}` | Actualizar alerta |
| `DELETE` | `/api/watchlist/{id}/alerts/{alertId}` | Eliminar alerta |

### Noticias

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/news` | Noticias de Google News |

### Exportación

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/export` | Descargar informe Markdown |
| `GET` | `/api/export/preview` | Previsualizar informe |

### Simuladores

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/simulator/projection` | Proyección de cartera |
| `POST` | `/api/simulator/mortgage` | Simulador hipotecario |
| `POST` | `/api/simulator/withdrawal` | Simulador de retiradas |
| `POST` | `/api/simulator/compound` | Interés compuesto |
| `POST` | `/api/simulator/montecarlo` | Simulación Monte Carlo |

### Plan de inversión

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/investment-plan` | Obtener plan actual |
| `PUT` | `/api/investment-plan` | Crear/actualizar plan |

### Flujos de caja planificados

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/planned-cashflows` | Listar todos |
| `GET` | `/api/planned-cashflows/pending` | Solo pendientes |
| `POST` | `/api/planned-cashflows` | Crear flujo |
| `PUT` | `/api/planned-cashflows/{id}` | Actualizar |
| `PATCH` | `/api/planned-cashflows/{id}/execute` | Marcar ejecutado |
| `DELETE` | `/api/planned-cashflows/{id}` | Eliminar |

### Horarios de mercado

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/market-schedules` | Listar horarios |
| `GET` | `/api/market-schedules/check?ticker=SXR8.DE` | Comprobar si mercado abierto |
| `POST` | `/api/market-schedules` | Crear horario |
| `PUT` | `/api/market-schedules/{id}` | Actualizar |
| `DELETE` | `/api/market-schedules/{id}` | Eliminar |

### Telegram

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/telegram/status` | Estado de configuración |
| `POST` | `/api/telegram/configure` | Configurar bot (en caliente) |
| `POST` | `/api/telegram/test` | Mensaje de prueba |
| `POST` | `/api/telegram/send` | Mensaje personalizado |
| `POST` | `/api/telegram/disable` | Desactivar |
| `GET` | `/api/telegram/channel/messages` | Mensajes del canal |
| `POST` | `/api/telegram/channel/poll` | Forzar polling |

### Registro de actividad

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/activity-log?category=PRICE&limit=200` | Log filtrable |
| `DELETE` | `/api/activity-log` | Borrar log |

---

## Tareas programadas

| Cron | Descripción |
|------|-------------|
| Cada 10 min | Actualización de precios (ligero/extendido alternados) |
| `0 0 0 * * *` | Snapshot precios de cierre |
| `0 55 23 * * *` | Snapshot diario del portfolio |
| `0 0 2 * * *` | Compactación histórico de precios |
| `0 0 18 * * MON-FRI` | Resumen diario Telegram |
