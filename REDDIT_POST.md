# 📊 Hice un Portfolio Tracker gratuito, 100% local y sin registro — solo necesitas Java [Open Source]

**TL;DR:** Me hice un tracker de inversiones para uso personal. Ha crecido tanto que creo que puede ser útil para más gente. Es gratis, open source, corre 100% en local (tus datos NUNCA salen de tu PC), no necesita registro ni cuenta en nada. Descomprime, ejecuta, usa.

---

## ¿Qué es?

Una app web local para gestionar tu cartera de inversiones con enfoque en **Dollar Cost Averaging (DCA)**. La arrancas, se abre en el navegador y listo. Sin cloud, sin suscripciones, sin telemetría.

## ¿Por qué otra app de portfolio?

Porque las que existen o te piden registro, o meten tus datos en la nube, o son de pago, o tienen features que no necesitas. Yo quería algo:

- **100% local** → tu base de datos es un archivo SQLite en tu disco
- **Sin registro** → descomprime, ejecuta, usa
- **Que calcule bien el DCA** → recalcula automáticamente precio medio y participaciones con cada compra o venta
- **Con métricas reales** → XIRR (TIR anualizada) calculado con flujos de caja reales, no aproximaciones
- **Con alertas que me avisen** → stop-loss, take-profit, objetivos DCA, alertas de precio... también por Telegram
- **Que me deje exportar un informe para pasárselo a ChatGPT/Claude** y que me analice la cartera

---

## 🚀 Features principales

### 📈 Dashboard completo
- KPIs principales: capital invertido, valor actual, P&L (no realizado + realizado), XIRR, inversión del mes con barra de progreso
- Tabla de posiciones con precio actual, variación diaria, P&L y TIR por posición
- Gráficos de distribución (tarta) y evolución de precios (Chart.js)
- **Posiciones cerradas**: cuando vendes todo, se mueve a una sección dedicada con el P&L realizado, periodo de tenencia y TIR

### 🔄 Gestión DCA — compras y ventas
- Registra compras (BUY) y ventas (SELL) con recalculo automático de precio medio y acciones
- **P&L realizado** por cada venta: `(precio venta − precio medio) × acciones vendidas`
- Vista previa del nuevo precio medio antes de confirmar
- **Importación masiva desde JSON** — migra toda tu cartera en segundos (crea posiciones automáticamente si no existen)

### ⚙️ Detalle operativo por posición (con asistente IA)
- Panel con 4 pestañas: Resumen, Límites, Notas y **🤖 IA**
- Stop-loss, take-profit, trailing stop, DCA target, alertas de precio, peso objetivo, nivel de riesgo, estrategia
- Indicadores visuales de distancia al precio actual para cada límite
- **Asistente IA**: genera un prompt con todos los datos de la posición → copia → pega en Claude/ChatGPT → te devuelve un JSON con recomendaciones → impórtalo con un clic y se rellenan todos los campos

### 💰 Precios en tiempo real
- Yahoo Finance cada 10 min (precio) / 30 min (precio + volumen)
- Conversión automática a EUR (USD, GBP, CHF, SEK, GBp...)
- **Horarios de mercado configurables** por bolsa: solo consulta cuando el mercado está abierto
- Ventana post-cierre de 10 min con reintentos automáticos para capturar el precio de cierre real
- Snapshot de cierre a medianoche para calcular variación diaria

### 🔔 Sistema de alertas avanzado
- **7 tipos**: Stop-Loss, Take-Profit, Trailing Stop, DCA Target, Precio ↑, Precio ↓, Peso desviado
- **3 severidades**: 🔴 Crítica, 🟡 Aviso, 🔵 Info
- Persistencia en localStorage (no reaparecen al recargar)
- Notificación automática por **Telegram** (DANGER y WARNING)

### 📊 Métricas y rentabilidad
- **XIRR** (TIR anualizada) global y por posición — calculado con flujos de caja reales
- **P&L realizado total** — suma de todas las ventas ejecutadas
- **Rentabilidad por periodos**: hoy, semana, mes, trimestre, YTD, 1 año — ajustada por DCA (descuenta aportaciones nuevas)
- **Equity curve**: valor de mercado vs capital invertido a lo largo del tiempo
- **Detalle de cartera**: pantalla completa con gráficos de barras por periodo

### 📉 Evolución patrimonial
- Snapshots diarios automáticos del valor de la cartera
- Gráfico de patrimonio con rangos seleccionables (1M, 3M, 6M, 1A, YTD, Todo)
- Historial agrupable por día, semana o mes según el periodo

### 👁️ Watchlist con alertas
- Seguimiento de activos que aún no has comprado
- Variación diaria/semanal/mensual + volumen actual vs media
- **Alertas configurables por activo**: precio arriba/abajo, volumen arriba/abajo (con cooldown de 24h)
- Notificación por Telegram cuando saltan

### 🧮 5 Simuladores financieros
- **Proyección de cartera**: aportaciones mensuales + rendimiento esperado
- **Simulador hipotecario**: cuota, amortización, intereses totales
- **Simulador de retiradas (FIRE)**: cuánto retirar al mes y cuánto dura tu capital
- **Interés compuesto**: crecimiento con aportaciones y reinversión
- **Monte Carlo**: simulación probabilística con miles de escenarios y percentiles

### ✈️ Integración Telegram (100% opcional)
- Alertas DANGER/WARNING en tiempo real tras cada actualización de precios
- **Resumen diario** de L-V a las 18:00: valor total, P&L del día, top gainers/losers, TIR, alertas activas
- Alertas de watchlist cuando se disparan
- Configuración en caliente desde la app (sin reiniciar, sin tocar archivos)
- Persistencia en BD — sobrevive a reinicios

### 📄 Informes optimizados para IA
- Exporta un informe Markdown completo diseñado para LLMs
- Incluye: resumen ejecutivo, detalle por posición, historial DCA, distribución, análisis de riesgo (HHI), plan de inversión, flujos planificados
- Pégalo en ChatGPT o Claude y pregúntale lo que quieras sobre tu cartera

### 🎯 Plan de inversión
- Define tu presupuesto mensual (fijo o variable) con barra de progreso en el dashboard
- Flujos de caja planificados: aportaciones extraordinarias o recurrentes con fecha, importe y estado (pendiente/ejecutado)

### Y más...
- 🕐 **Horarios de mercado** configurables por bolsa (`.DE`, `.L`, `.MC`...)
- 📱 **Versión móvil** con detección automática de User-Agent
- 🌙 **Tema claro / oscuro** con persistencia
- 📋 **Registro de actividad** filtrable (precios, Telegram, DCA, alertas, snapshots...)
- 🧹 **Compactación automática** del histórico de precios (retención escalonada: hoy todos, semana 1/hora, mes 1/día, año 1/semana)
- 📰 **Noticias** de Google News por posición (ticker, nombre, sector)
- 📖 **Guía de usuario completa** integrada en la app

---

## Stack

| Componente | Tecnología |
|---|---|
| Backend | Spring Boot 4 / Java 21 |
| Base de datos | SQLite (un archivo, zero config) |
| Frontend | HTML + JS vanilla + Chart.js |
| Precios | Yahoo Finance + conversión automática EUR |
| Noticias | Google News (RSS) |
| Notificaciones | Telegram Bot API (opcional) |
| Tipos de cambio | open.er-api.com (caché 6h) |

---

## Arranque en 2 minutos

1. Descarga el ZIP de releases
2. Descomprime
3. Ejecuta `start.bat` (Windows) o `./start.sh` (Linux/Mac)
4. Se abre `http://localhost:19480/portfoliotracker/`

**Requisito único:** tener Java 21 instalado.

- Se puede instalar como **servicio de Windows** para que arranque solo con el PC
- Viene con **datos de ejemplo** (seed) para que puedas probar sin meter nada tuyo
- Escucha en `0.0.0.0` → accesible desde **cualquier dispositivo de tu red** (incluido el móvil)

---

## Screenshots

*(capturas del dashboard, detalle de posición con pestaña IA, simulador Monte Carlo, panel de alertas/Telegram, versión móvil)*

---

**Es gratis, no hay trampa.** Lo hice para mí, lo comparto por si a alguien le sirve. Feedback bienvenido, y si alguien quiere contribuir, PRs abiertas.

**Repo:** *(enlace a GitHub)*

---

## Subreddits recomendados para publicar

| Subreddit | Por qué encaja |
|---|---|
| **r/selfhosted** | App 100% local, sin cloud, instalable como servicio |
| **r/investing** | Gestión DCA, métricas XIRR, alertas operativas |
| **r/Bogleheads** | Enfoque DCA pasivo, simuladores, planificación |
| **r/FIRE** | Simulador de retiradas, planificación financiera |
| **r/sideproject** | Proyecto personal bien pulido |
| **r/java** | Spring Boot 4, Java 21, arquitectura limpia |
| **r/homelab** | Self-hosted, accesible en red local |
| **r/SpainFIRE** | Público español, conversión automática a EUR |
| **r/eupersonalfinance** | Público europeo, divisas EU soportadas |
| **r/programming** | Proyecto técnico interesante |

