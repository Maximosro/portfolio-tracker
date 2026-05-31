# Research: modernize-ui-ux

## Problema
La aplicación Portfolio Tracker es funcionalmente completa pero la capa de presentación tiene deuda técnica acumulada: un frontend monolítico de 5.179 líneas en un solo archivo HTML, estilos duplicados entre páginas, separación artificial desktop/mobile, y carencia de patrones modernos de UX (skeleton screens, transiciones, componentes reutilizables). Se busca identificar oportunidades de mejora visual y de usabilidad sin reescribir el stack tecnológico.

## Stack tecnológico (restricciones)
- **Backend:** Spring Boot 4.0.5, Java 21, SQLite (Hibernate), sin frontend bundler
- **Frontend:** HTML + CSS + JavaScript vanilla, servido como recursos estáticos por Spring Boot
- **Dependencias CDN:** Chart.js 4.4.0, chartjs-plugin-annotation 3.0.1, chartjs-adapter-date-fns 3.0.0, jsPDF 2.5.1, jsPDF-AutoTable 3.8.2
- **Fuente:** Inter (Google Fonts, CDN)
- **Sin:** npm, Webpack, Vite, React, Vue, Tailwind, TypeScript

## Código / assets relevantes
- `src/main/resources/static/index.html:1-5179` — Página principal (dashboard, posiciones, DCA, watchlist, alertas, noticias). 5179 líneas monolíticas con CSS, HTML y JS en un solo archivo.
- `src/main/resources/static/mobile.html:1-1458` — Versión móvil separada con su propia UI (tab bar navigation, diseño específico mobile). Duplica design tokens y lógica.
- `src/main/resources/static/simuladores.html:1-1710` — Simuladores financieros (jubilación, DCA, interés compuesto). Duplica design tokens.
- `src/main/resources/static/guia.html:1-1610` — Guía de usuario con sidebar navigation. Usa variables CSS diferentes a las otras páginas (hex en vez de oklch).
- `src/main/resources/application.yaml` — Configuración: puerto 19480, context-path /portfoliotracker
- `pom.xml` — Spring Boot 4.0.5, Java 21, sqlite-jdbc 3.49.1.0, sin dependencias de template engine (no Thymeleaf, no Mustache)
- `src/main/java/com/sro/myportfoliotracker/controller/` — 17 controladores REST: PositionController, DcaController, PriceController, WatchlistController, AlertController, ExportController, MetricsController, SnapshotController, SimulatorController, etc.

## Flujo actual

```
Browser → Spring Boot (static resources)
  ├── index.html (desktop) ─── fetch() → REST API (/api/*)
  ├── mobile.html (responsive separado) ─── fetch() → REST API
  ├── simuladores.html ─── fetch() → REST API
  └── guia.html (estático)
```

### Arquitectura del frontend (index.html)
```
CSS: ~300 líneas de custom properties + clases utilitarias
  ├── Design tokens (color, radius, shadow, space, font)
  ├── Layout (.app, header, .kpi-grid, .section)
  ├── Componentes (botones, inputs, tablas, badges, toasts)
  ├── Paneles (news, alerts, activity log)
  └── Responsive (@media queries al final)

HTML: ~350 líneas de estructura
  ├── Header con botones de acción
  ├── KPI cards (4 métricas principales)
  ├── Charts (evolución precio + cartera)
  ├── Posiciones (tabla)
  ├── Posiciones cerradas (tabla colapsable)
  ├── Watchlist (tabla + FAB para añadir)
  ├── Historial DCA (tabla con paginación)
  └── Modales: posición, DCA, detalle posición, importación JSON, period history

JS: ~4500 líneas de lógica vanilla
  ├── Estado global (positions[], dcaEntries[], watchlist[], alerts[])
  ├── Fetch wrappers (loadPositions, loadDca, loadWatchlist, etc.)
  ├── Renderizado manual (innerHTML para tablas, modales, KPIs)
  ├── Charts (Chart.js: historyChart, pdHistChart)
  ├── CRUD de posiciones, DCA, watchlist
  ├── Panel de noticias (Google News RSS scraping)
  ├── Panel de alertas (stop-loss, take-profit, trailing stop)
  ├── Activity log
  └── Tema claro/oscuro (localStorage)
```

## Patrones existentes

1. **Design tokens con CSS custom properties**: Sistema de variables de diseño consistente con temas claro/oscuro usando oklch(). Bien estructurado pero duplicado en 3 archivos y ausente en guia.html.

2. **Componentes embebidos en el HTML**: Cada modal es un `<div>` con `display:none` que se muestra/oculta con JavaScript. No hay abstracción de componente — cada modal tiene su propio patrón de open/close.

3. **Renderizado con innerHTML**: Las tablas y paneles se construyen concatenando strings HTML en JavaScript. Sin sanitización ni template literals con escaping.

4. **Fetch API directo**: Comunicación con el backend vía `fetch()` a endpoints REST. Sin capa de abstracción (no hay API client).

5. **Estado global mutable**: Arrays `positions`, `dcaEntries`, `watchlist` como variables globales mutadas directamente.

6. **Gráficos con Chart.js**: Instanciación directa de `new Chart()` con destrucción manual del canvas previo. Configuraciones repetitivas entre gráficos.

7. **Panel FAB pattern**: Paneles flotantes (noticias, alertas, activity log) activados por botones FAB con animaciones slide-up.

## Hallazgos clave

### Lo que ya funciona bien
- Design tokens con oklch() — moderna, accesible, con buena semántica de color
- Tema claro/oscuro con transición suave
- Inter como fuente — excelente legibilidad
- KPI cards con buena jerarquía visual
- Paneles deslizables con animaciones sutiles
- Tablas compactas con buena densidad de información
- Estados vacíos y de error presentes en varios componentes
- Chart.js con anotaciones de horario de mercado

### Problemas identificados

| Área | Problema | Severidad |
|------|----------|-----------|
| **Arquitectura** | Monolito de 5179 líneas (HTML+CSS+JS en un solo archivo) | Alta |
| **DRY** | Design tokens duplicados en 3 archivos. CSS duplicado entre páginas. | Alta |
| **Mobile** | Página móvil separada (`mobile.html`) en vez de responsive design. Lógica y estilos duplicados. | Alta |
| **Modales** | 5+ modales con patrón manual display:none/flex. Sin accesibilidad (no focus trap, no ESC, no aria). Sin backdrop click para cerrar consistente. | Media |
| **Tablas** | Único modo de visualización de datos. Sin vista de tarjetas, sin ordenación, sin filtros avanzados. | Media |
| **Loading states** | Sin skeleton screens ni loading spinners en KPIs/tablas. Solo aparece texto "Cargando..." en algunas secciones. | Media |
| **Transiciones** | Limitadas a animaciones de entrada (slideIn, newsSlideUp). Sin transiciones entre vistas o al cargar datos. | Media |
| **Chart.js** | Versión 4.4.0 (hay 4.5+ disponible). Código de charts repetitivo con config duplicada. | Baja |
| **Accesibilidad** | Sin roles ARIA, sin navegación por teclado, sin focus management. | Media |
| **Formularios** | Validación mínima. Sin feedback visual de errores inline. Sin autocompletado de tickers. | Baja |
| **Watchlist** | Solo tabla. Sin vista de tarjetas con mini-gráficos sparkline. | Baja |
| **guia.html** | Estilos inconsistentes con el resto de la app (hex colors vs oklch, fuente distinta). | Baja |
| **Rendimiento** | Sin lazy loading. Chart.js carga siempre aunque no se usen gráficos. Sin debounce en events. | Baja |
| **Consistencia visual** | Inline styles mezclados con clases CSS. Algunos estilos inline usan var(), otros valores hardcodeados. | Media |

## Preguntas / Decisiones

- **P1 — Alcance de la separación de archivos**: Extraer CSS y JS a archivos separados. CSS compartido (`styles.css`) entre desktop y simuladores. JS modularizado por funcionalidad.
  → **Decidido: separar en archivos independientes.**

- **P2 — Estrategia mobile**: Mantener `mobile.html` como página separada, no migrar a responsive único.
  → **Decidido: mantener mobile.html independiente.**

- **P3 — Componentes reutilizables**: Alpine.js (~15KB CDN) vs vanilla JS. Se crearon prototipos de ambas opciones para comparar.
  → **Decidido: Alpine.js.** Ver `alpine-prototype.html` — elimina manipulación manual del DOM, da transiciones declarativas, validación inline reactiva, y vista previa de cálculos en tiempo real sin código boilerplate. El coste (15KB) es negligible frente al ahorro en líneas de JS.

- **P4 — Reorganización del header (desktop)**: Solo las alertas se quedan en el header. El resto de botones (Exportar, Detalle de Cartera, Nueva posición, Nueva operación, Simuladores, Guía) deben moverse a un menú de navegación.
  → **Decidido: header minimalista, solo alertas + theme toggle. Resto → menú.**

- **P5 — Tema visual**: Desktop mantiene el rollo «terminal financiera» (oscuro, denso, tabular). Mobile debe ser más visual y condensado, con keycards, más gráfico y menos tabla.
  → **Decidido: desktop = terminal financiera, mobile = visual/keycards.**
