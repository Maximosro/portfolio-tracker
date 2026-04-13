# 📊 Portfolio Tracker

Aplicación de escritorio/web para seguimiento de inversiones con estrategia **Dollar Cost Averaging (DCA)**.  
Gestiona posiciones, historial de compras, alertas operativas, watchlist y genera informes para análisis con IA.

## Stack

- **Backend:** Spring Boot 4.0.5 / Java 21
- **Base de datos:** SQLite (`data/portfolio.db`)
- **Frontend:** HTML + JavaScript vanilla (Chart.js)
- **Precios:** Yahoo Finance (conversión automática a EUR)

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

## Base de datos

- Archivo SQLite en `data/portfolio.db` (ruta relativa al directorio de ejecución)
- Se crea automáticamente al arrancar si no existe
- Si la BD está vacía, se ejecutan los seeds (busca primero en `./data/`, luego dentro del JAR)
- `ddl-auto: update` — Hibernate gestiona el esquema automáticamente

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
| `POST` | `/api/dca` | Registrar compra DCA (recalcula posición desde todos los DCA) |
| `PUT` | `/api/dca/{id}` | Modificar entrada DCA (recalcula posición) |
| `DELETE` | `/api/dca/{id}` | Eliminar entrada DCA (recalcula posición) |

> Al añadir, modificar o eliminar un DCA, `shares` y `avgPrice` de la posición se **recalculan automáticamente** desde todos los registros DCA existentes.

### Detalle operativo por posición

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/position-details` | Listar todos los detalles operativos |
| `GET` | `/api/positions/{ticker}/detail` | Obtener detalle de una posición |
| `PUT` | `/api/positions/{ticker}/detail` | Guardar/actualizar detalle (stop-loss, take-profit, notas, etc.) |

### Precios

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/api/prices/refresh` | Forzar actualización de precios (Yahoo Finance → EUR) |
| `GET` | `/api/prices/last-update` | Timestamp de última actualización |
| `GET` | `/api/prices/history/{ticker}?range=1m` | Histórico de precios de un ticker |
| `GET` | `/api/prices/history?range=1m` | Histórico global de todas las posiciones |

Rangos válidos para `range`: `1d`, `1w`, `1m`, `3m`, `6m`, `1y`, `ytd`, `all`

### Métricas

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/metrics` | XIRR global y por posición (calculado desde flujos DCA reales) |
| `GET` | `/api/metrics/returns` | Rentabilidad por periodos (hoy, semana, mes, trimestre, YTD, año) |

### Snapshots y Evolución Patrimonial

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/snapshots?range=3m` | Snapshots del portfolio (valor + invertido) filtrados por rango |
| `GET` | `/api/snapshots/period-history?period=week` | Histórico detallado de un periodo con retornos ajustados por DCA |

Rangos válidos para `range`: `1m`, `3m`, `6m`, `1y`, `ytd`, `all`  
Periodos válidos para `period`: `week`, `month`, `quarter`, `ytd`, `year`

> Los retornos se calculan como **cambio en P&L** (valor - invertido), lo que descuenta automáticamente las aportaciones DCA. Se agrupan por día (semana/mes), por semana (trimestre/YTD) o por mes (año).

### Alertas

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/alerts` | Alertas activas (stop-loss, take-profit, trailing stop, DCA target, peso objetivo) |

### Watchlist

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/watchlist` | Listar watchlist |
| `GET` | `/api/watchlist/{id}` | Obtener item |
| `POST` | `/api/watchlist` | Añadir a watchlist |
| `PUT` | `/api/watchlist/{id}` | Actualizar item |
| `DELETE` | `/api/watchlist/{id}` | Eliminar de watchlist |
| `POST` | `/api/watchlist/refresh` | Forzar actualización de precios de la watchlist |

### Noticias

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/news` | Noticias recientes de Google News para cada posición |

### Exportación / Informes

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/export` | Descargar informe Markdown completo del portfolio |
| `GET` | `/api/export/preview` | Previsualizar informe como texto plano |

> El informe incluye: resumen ejecutivo, detalle por posición, historial DCA, análisis de allocation, evolución de precios, indicadores de riesgo y más. Diseñado para ser usado como contexto en conversaciones con IA.

---

## Funcionalidades clave

### Actualización automática de precios
- **Cada 30 minutos** vía Yahoo Finance
- Conversión automática a EUR usando tasas de `open.er-api.com` (caché de 6 horas)
- Cada precio se guarda en `price_history` para gráficos y consultas históricas
- Divisas soportadas: EUR, USD, GBP, GBp/GBX, CHF, SEK y cualquier otra devuelta por la API

### Sistema de alertas
- **Stop-Loss / Take-Profit**: alerta cuando el precio alcanza los límites configurados
- **Trailing Stop**: alerta basada en porcentaje de caída desde precio medio
- **DCA Target**: notifica cuando el precio baja al nivel objetivo de compra
- **Peso objetivo**: alerta si la ponderación se desvía ≥5pp del target configurado
- Las alertas leídas se **persisten en localStorage** (no reaparecen al recargar)

### Consistencia de datos
- Eliminar una posición borra en cascada: DCA, historial de precios y detalle operativo
- Cualquier cambio en el historial DCA (añadir/editar/eliminar) recalcula automáticamente `shares` y `avgPrice`

### XIRR (TIR anualizada)
- Calculado con los flujos DCA reales como cash-flows negativos
- Valor actual de mercado como flujo positivo terminal
- Se calcula por posición individual y para la cartera global
