package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.InvestmentPlan;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PositionDetail;
import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.InvestmentPlanRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Genera un informe completo del portfolio en formato Markdown, diseñado para ser consumido por un
 * LLM (Claude) y poder responder preguntas detalladas sobre la cartera.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

  private final PositionRepository positionRepository;
  private final DcaEntryRepository dcaEntryRepository;
  private final PriceHistoryRepository priceHistoryRepository;
  private final PortfolioMetricsService metricsService;
  private final PositionDetailService positionDetailService;
  private final AlertService alertService;
  private final InvestmentPlanRepository investmentPlanRepository;

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern(
      "dd/MM/yyyy HH:mm");
  private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

  public String generateReport() {
    List<Position> positions = positionRepository.findAll();
    List<DcaEntry> dcaEntries = dcaEntryRepository.findAllByOrderByDateDesc();
    PortfolioMetricsDto metrics = metricsService.calculateMetrics();
    List<PositionDetail> details = positionDetailService.findAll();
    Map<String, PositionDetail> detailMap = details.stream()
        .collect(Collectors.toMap(PositionDetail::getTicker, d -> d));
    List<AlertDto> alerts = alertService.checkAlerts();
    InvestmentPlan plan = investmentPlanRepository.findById(1L).orElse(null);

    // Separar posiciones activas y cerradas
    List<Position> activePositions = positions.stream().filter(p -> p.getShares() > 0).toList();
    List<Position> closedPositions = positions.stream().filter(p -> p.getShares() <= 0).toList();

    // Valoración unificada de posiciones activas (regla única: fallback a avgPrice sin precio)
    Map<String, PositionValuation> valuations = new LinkedHashMap<>();
    double totalInvested = 0, totalValue = 0, totalPL = 0;
    for (Position p : activePositions) {
      double invested = p.getShares() * p.getAvgPrice();
      double price = p.getCurrentPrice() != null ? p.getCurrentPrice() : p.getAvgPrice();
      double value = p.getShares() * price;
      double pl = value - invested;
      double plPct = invested > 0 ? (pl / invested) * 100 : 0;
      valuations.put(p.getTicker(), new PositionValuation(invested, value, pl, plPct));
      totalInvested += invested;
      totalValue += value;
      totalPL += pl;
    }
    double totalPLPct = totalInvested > 0 ? (totalPL / totalInvested) * 100 : 0;
    PositionTotals totals = new PositionTotals(totalInvested, totalValue, totalPL, totalPLPct);

    StringBuilder sb = new StringBuilder();

    appendHeader(sb);
    appendExecutiveSummary(sb, positions, activePositions, closedPositions, metrics, dcaEntries,
        valuations, totals);
    appendInvestmentPlan(sb, dcaEntries, plan);
    appendRealInvestment(sb, dcaEntries, plan);
    appendActiveAlerts(sb, alerts);
    appendPositionsDetail(sb, activePositions, metrics, dcaEntries, valuations, totals);
    appendSalesSummary(sb, dcaEntries, metrics);
    appendOperationalDetail(sb, activePositions, detailMap);
    appendAllocationAnalysis(sb, activePositions, detailMap, valuations, totals);
    appendPriceEvolution(sb, activePositions);
    appendRiskAnalysis(sb, activePositions, metrics, valuations, totals);
    appendContextNotes(sb, positions);

    return sb.toString();
  }

  // ───────────────────── HEADER ─────────────────────

  private void appendHeader(StringBuilder sb) {
    String now = LocalDateTime.now(ZONE).format(DATETIME_FMT);
    sb.append("# 📊 Informe Completo del Portfolio\n\n");
    sb.append("> **Fecha de generación:** ").append(now).append(" (Europe/Madrid)\n");
    sb.append("> **Moneda base:** EUR\n");
    sb.append("> **Sistema:** Portfolio Tracker — Exportación para análisis con IA\n\n");
    sb.append("---\n\n");
    sb.append("""
        > **NOTA PARA EL ASISTENTE:** Snapshot de una cartera de inversión real. Importes en EUR salvo
        > indicación. Los precios actuales provienen de Yahoo Finance y se convierten automáticamente a EUR.
        > El XIRR se calcula con los flujos DCA reales. Las métricas están unificadas: si una cifra aparece
        > en varias secciones, es el mismo valor.
        
        """);
  }

  // ───────────────────── RESUMEN EJECUTIVO ─────────────────────

  private void appendExecutiveSummary(StringBuilder sb, List<Position> positions,
      List<Position> activePositions, List<Position> closedPositions,
      PortfolioMetricsDto metrics, List<DcaEntry> dcaEntries,
      Map<String, PositionValuation> valuations, PositionTotals totals) {
    // Totales unificados (calculados una sola vez en generateReport)
    double totalInvested = totals.invested();
    double totalValue = totals.value();
    double unrealizedPL = totals.pl();
    double unrealizedPLPct = totals.plPct();
    long positionsWithPrice = activePositions.stream().filter(p -> p.getCurrentPrice() != null)
        .count();

    // P&L realizado total (ventas parciales + cierres)
    double totalRealizedPL = metrics.totalRealizedPL() != null ? metrics.totalRealizedPL() : 0.0;

    // Total de operaciones de venta
    long totalSellOps = dcaEntries.stream().filter(e -> "SELL".equals(e.getType())).count();
    long totalBuyOps = dcaEntries.stream().filter(e -> !"SELL".equals(e.getType())).count();

    sb.append("## 1. Resumen Ejecutivo\n\n");
    sb.append("| Métrica | Valor |\n");
    sb.append("|---------|-------|\n");
    sb.append(String.format("| **Posiciones activas** | %d |\n", activePositions.size()));
    sb.append(String.format("| **Posiciones cerradas** | %d |\n", closedPositions.size()));
    sb.append(String.format("| **Total posiciones** | %d |\n", positions.size()));
    sb.append(String.format("| **Posiciones con precio actualizado** | %d de %d activas |\n",
        positionsWithPrice, activePositions.size()));
    sb.append(String.format("| **Capital invertido (activas)** | %s |\n", fmtEur(totalInvested)));
    sb.append(String.format("| **Valor actual de mercado** | %s |\n", fmtEur(totalValue)));
    sb.append(String.format("| **P&L no realizado (activas)** | %s (%s) |\n", fmtEur(unrealizedPL),
        fmtPct(unrealizedPLPct)));
    sb.append(String.format("| **P&L realizado (ventas)** | %s %s |\n", fmtEur(totalRealizedPL),
        totalRealizedPL >= 0 ? "✅" : "❌"));
    sb.append(String.format("| **P&L total (realizado + no realizado)** | %s |\n",
        fmtEur(unrealizedPL + totalRealizedPL)));
    sb.append(String.format("| **Operaciones de compra** | %d |\n", totalBuyOps));
    sb.append(String.format("| **Operaciones de venta** | %d |\n", totalSellOps));
    sb.append(String.format("| **XIRR (TIR anualizada) cartera** | %s |\n",
        metrics.portfolioXirr() != null ? fmtPct(metrics.portfolioXirr() * 100) : "N/D"));
    sb.append("\n");

    // Mejor y peor posición activa
    Position best = null, worst = null;
    double bestPct = Double.NEGATIVE_INFINITY, worstPct = Double.POSITIVE_INFINITY;
    for (Position p : activePositions) {
      if (p.getCurrentPrice() == null) {
        continue;
      }
      double inv = p.getShares() * p.getAvgPrice();
      double val = p.getShares() * p.getCurrentPrice();
      double pct = inv > 0 ? ((val - inv) / inv) * 100 : 0;
      if (pct > bestPct) {
        bestPct = pct;
        best = p;
      }
      if (pct < worstPct) {
        worstPct = pct;
        worst = p;
      }
    }
    if (best != null) {
      sb.append(String.format("- 🏆 **Mejor posición activa:** %s (%s) → %s\n", best.getTicker(),
          best.getName(), fmtPct(bestPct)));
    }
    if (worst != null) {
      sb.append(String.format("- 📉 **Peor posición activa:** %s (%s) → %s\n", worst.getTicker(),
          worst.getName(), fmtPct(worstPct)));
    }
    sb.append("\n");

    // Antigüedad de posiciones (fecha primera compra desde DCA)
    Map<String, LocalDate> firstBuyByTicker = dcaEntries.stream()
        .filter(e -> !"SELL".equals(e.getType()))
        .collect(Collectors.groupingBy(DcaEntry::getTicker,
            Collectors.collectingAndThen(
                Collectors.minBy(Comparator.comparing(DcaEntry::getDate)),
                opt -> opt.map(DcaEntry::getDate).orElse(null))));

    List<Map.Entry<String, LocalDate>> activeFirstBuys = firstBuyByTicker.entrySet().stream()
        .filter(e -> e.getValue() != null && activePositions.stream()
            .anyMatch(p -> p.getTicker().equals(e.getKey())))
        .sorted(Map.Entry.comparingByValue())
        .toList();

    if (!activeFirstBuys.isEmpty()) {
      Map.Entry<String, LocalDate> oldest = activeFirstBuys.getFirst();
      Map.Entry<String, LocalDate> newest = activeFirstBuys.getLast();
      long avgDays = (long) activeFirstBuys.stream()
          .mapToLong(e -> ChronoUnit.DAYS.between(e.getValue(), LocalDate.now()))
          .average().orElse(0);
      sb.append(String.format("- 📅 **Posición más antigua:** %s (desde %s, hace %d días)\n",
          oldest.getKey(), oldest.getValue().format(DATE_FMT),
          ChronoUnit.DAYS.between(oldest.getValue(), LocalDate.now())));
      sb.append(String.format("- 🆕 **Posición más reciente:** %s (desde %s, hace %d días)\n",
          newest.getKey(), newest.getValue().format(DATE_FMT),
          ChronoUnit.DAYS.between(newest.getValue(), LocalDate.now())));
      sb.append(
          String.format("- ⏱️ **Antigüedad media de posiciones activas:** %d días\n", avgDays));
    }
    sb.append("\n");
  }

  // ───────────────────── PLAN DE INVERSIÓN ─────────────────────

  private void appendInvestmentPlan(StringBuilder sb, List<DcaEntry> dcaEntries,
      InvestmentPlan plan) {
    sb.append("## 2. Plan de Inversión Mensual\n\n");

    if (plan == null || plan.getMonthlyBudget() == null || plan.getMonthlyBudget() <= 0) {
      sb.append("⚠️ **No hay plan de inversión mensual configurado.**\n\n");
    } else {
      sb.append(String.format("| Concepto | Valor |\n"));
      sb.append("|----------|-------|\n");
      sb.append(
          String.format("| **Aportación mensual** | %s |\n", fmtEur(plan.getMonthlyBudget())));
      sb.append(String.format("| **Tipo** | %s |\n",
          plan.getBudgetType() != null ? plan.getBudgetType() : "FIJO"));
      if (plan.getNotes() != null && !plan.getNotes().isBlank()) {
        sb.append(String.format("| **Notas** | %s |\n", plan.getNotes()));
      }
      if (plan.getUpdatedAt() != null) {
        sb.append(String.format("| **Última actualización** | %s |\n",
            plan.getUpdatedAt().atZone(ZONE).format(DATETIME_FMT)));
      }
      sb.append("\n");
    }

    // Reparto DCA por posición
    List<PositionDetail> allDetails = positionDetailService.findAll();
    List<PositionDetail> withDca = allDetails.stream()
        .filter(d -> d.getMonthlyDcaAmount() != null && d.getMonthlyDcaAmount() > 0)
        .sorted(Comparator.comparingDouble(PositionDetail::getMonthlyDcaAmount).reversed())
        .toList();
    if (!withDca.isEmpty()) {
      double totalDca = withDca.stream().mapToDouble(PositionDetail::getMonthlyDcaAmount).sum();
      sb.append("### Reparto DCA mensual por posición\n\n");
      sb.append("| Ticker | DCA Mensual (€) | % del Presupuesto |\n");
      sb.append("|--------|-----------------|-------------------|\n");
      for (PositionDetail d : withDca) {
        double pct = plan != null && plan.getMonthlyBudget() != null && plan.getMonthlyBudget() > 0
            ? (d.getMonthlyDcaAmount() / plan.getMonthlyBudget()) * 100 : 0;
        sb.append(String.format("| **%s** | %s | %s |\n",
            d.getTicker(), fmtEur(d.getMonthlyDcaAmount()), pct > 0 ? fmtPct(pct) : "—"));
      }
      sb.append(String.format("| **TOTAL** | **%s** | %s |\n", fmtEur(totalDca),
          plan != null && plan.getMonthlyBudget() != null && plan.getMonthlyBudget() > 0
              ? fmtPct((totalDca / plan.getMonthlyBudget()) * 100) : "—"));
      sb.append("\n");
    }
  }

  // ───────────────────── INVERSIÓN REAL 6 MESES ─────────────────────

  private void appendRealInvestment(StringBuilder sb, List<DcaEntry> dcaEntries,
      InvestmentPlan plan) {
    sb.append("## 3. Inversión Real Últimos 6 Meses Completos\n\n");

    // Comparar con la media real de los últimos 6 meses COMPLETOS (excluye mes actual)
    LocalDate firstDayCurrentMonth = LocalDate.now().withDayOfMonth(1);
    LocalDate firstDayThreeMonthsAgo = firstDayCurrentMonth.minusMonths(6);
    Map<String, Double> recentMonthly = new TreeMap<>();
    for (DcaEntry e : dcaEntries) {
      if (!"SELL".equals(e.getType())
          && !e.getDate().isBefore(firstDayThreeMonthsAgo)
          && e.getDate().isBefore(firstDayCurrentMonth)) {
        String key =
            e.getDate().getYear() + "-" + String.format("%02d", e.getDate().getMonthValue());
        recentMonthly.merge(key, e.getShares() * e.getPrice(), Double::sum);
      }
    }

    if (!recentMonthly.isEmpty()) {
      double avgMonthly = recentMonthly.values().stream().mapToDouble(v -> v).average().orElse(0);
      sb.append("| Mes | Invertido (€) | Nota |\n");
      sb.append("|-----|---------------|------|\n");
      recentMonthly.forEach((k, v) -> sb.append(String.format("| %s | %s | |\n", k, fmtEur(v))));
      sb.append(String.format("| **Media mensual** | **%s** | Solo meses completos |\n",
          fmtEur(avgMonthly)));

      // Mostrar mes actual (parcial) como referencia
      String currentMonthKey = firstDayCurrentMonth.getYear() + "-" + String.format("%02d",
          firstDayCurrentMonth.getMonthValue());
      double currentMonthTotal = dcaEntries.stream()
          .filter(e -> !"SELL".equals(e.getType())
              && !e.getDate().isBefore(firstDayCurrentMonth))
          .mapToDouble(e -> e.getShares() * e.getPrice())
          .sum();
      if (currentMonthTotal > 0) {
        sb.append(String.format("| %s | %s | ⏳ Mes en curso (parcial) |\n", currentMonthKey,
            fmtEur(currentMonthTotal)));
      }
      sb.append("\n");

      if (plan != null && plan.getMonthlyBudget() != null && plan.getMonthlyBudget() > 0) {
        double deviation = ((avgMonthly - plan.getMonthlyBudget()) / plan.getMonthlyBudget()) * 100;
        sb.append(String.format("- Desviación media vs objetivo: **%s** %s\n\n",
            fmtPct(deviation), Math.abs(deviation) >= 20 ? "⚠️" : "✅"));
      }
    } else {
      sb.append("*Sin datos de inversión en los últimos 6 meses completos.*\n\n");
    }
  }


  private void appendActiveAlerts(StringBuilder sb, List<AlertDto> alerts) {
    sb.append("## 4. Alertas Activas\n\n");

    if (alerts.isEmpty()) {
      sb.append("✅ **Sin alertas activas.**\n\n");
      return;
    }

    sb.append(String.format("⚠️ **%d alerta(s) activa(s)**\n\n", alerts.size()));
    sb.append("| Severidad | Ticker | Tipo | Mensaje |\n");
    sb.append("|-----------|--------|------|---------|\n");

    for (AlertDto alert : alerts) {
      String sevIcon = switch (alert.getSeverity()) {
        case "DANGER" -> "🔴 Crítica";
        case "WARNING" -> "🟡 Aviso";
        case "INFO" -> "🔵 Info";
        default -> alert.getSeverity();
      };
      String typeLabel = switch (alert.getType()) {
        case "ALERT_ABOVE" -> "Alerta ↑";
        case "ALERT_BELOW" -> "Alerta ↓";
        default -> alert.getType();
      };
      sb.append(String.format("| %s | **%s** | %s | %s |\n",
          sevIcon, alert.getTicker(), typeLabel, alert.getMessage()));
    }
    sb.append("\n");
  }

  // ───────────────────── DETALLE POR POSICIÓN ─────────────────────

  private void appendPositionsDetail(StringBuilder sb, List<Position> positions,
      PortfolioMetricsDto metrics, List<DcaEntry> dcaEntries,
      Map<String, PositionValuation> valuations, PositionTotals totals) {
    sb.append("## 5. Detalle por Posición\n\n");
    sb.append(
        "| Ticker | Nombre | Sector | Acciones | P.Medio (€) | P.Actual (€) | Var. Día | Invertido (€) | Valor (€) | P&L (€) | P&L (%) | XIRR | Primera Compra |\n");
    sb.append(
        "|--------|--------|--------|----------|-------------|---------------|------------|----------|---------------|-----------|---------|------|----------------|\n");

    // Pre-calcular primera compra por ticker
    Map<String, LocalDate> firstBuyByTicker = dcaEntries.stream()
        .filter(e -> !"SELL".equals(e.getType()))
        .collect(Collectors.groupingBy(DcaEntry::getTicker,
            Collectors.collectingAndThen(
                Collectors.minBy(Comparator.comparing(DcaEntry::getDate)),
                opt -> opt.map(DcaEntry::getDate).orElse(null))));

    for (Position p : positions) {
      PositionValuation v = valuations.get(p.getTicker());
      Double xirr =
          metrics.positionXirr() != null ? metrics.positionXirr().get(p.getTicker()) : null;
      String dayChange = "—";
      if (p.getCurrentPrice() != null && p.getPreviousClose() != null && p.getPreviousClose() > 0) {
        double pct = ((p.getCurrentPrice() - p.getPreviousClose()) / p.getPreviousClose()) * 100;
        dayChange = fmtPct(pct);
      }

      // Primera compra
      LocalDate firstBuy = firstBuyByTicker.get(p.getTicker());
      String firstBuyStr = firstBuy != null ? firstBuy.format(DATE_FMT) : "—";

      sb.append(String.format(
          "| **%s** | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n",
          p.getTicker(),
          nvl(p.getName()),
          nvl(p.getSector()),
          fmtNum(p.getShares(), 6),
          fmtNum(p.getAvgPrice(), 4),
          p.getCurrentPrice() != null ? fmtNum(p.getCurrentPrice(), 4) : "—",
          dayChange,
          fmtEur(v.invested()),
          fmtEur(v.value()),
          fmtEur(v.pl()),
          fmtPct(v.plPct()),
          xirr != null ? fmtPct(xirr * 100) : "N/D",
          firstBuyStr
      ));
    }

    sb.append(String.format(
        "| **TOTAL** | | | | | | | **%s** | **%s** | **%s** | **%s** | **%s** | |\n",
        fmtEur(totals.invested()), fmtEur(totals.value()), fmtEur(totals.pl()),
        fmtPct(totals.plPct()),
        metrics.portfolioXirr() != null ? fmtPct(metrics.portfolioXirr() * 100) : "N/D"));
    sb.append("\n");

    // Leyenda de columnas
    sb.append("> **Leyenda:** P.Medio = precio medio de compra ponderado · P.Actual = último precio "
        + "(EUR) · Var.Día = variación vs cierre anterior · Invertido = acciones × P.Medio · "
        + "Valor = acciones × P.Actual · P&L = Valor − Invertido · XIRR = rentabilidad "
        + "anualizada · Primera Compra = fecha del primer DCA de compra.\n\n");

    // Info adicional por posición
    sb.append("### Información adicional por posición\n\n");
    for (Position p : positions) {
      String lastUpdate = p.getLastPriceUpdate() != null
          ? p.getLastPriceUpdate().atZone(ZONE).format(DATETIME_FMT)
          : "Sin actualización automática";
      sb.append(String.format("- **%s** — Yahoo: `%s` | Última actualización precio: %s\n",
          p.getTicker(), nvl(p.getYahooTicker()), lastUpdate));
    }
    sb.append("\n");
  }


  private void appendSalesSummary(StringBuilder sb, List<DcaEntry> dcaEntries,
      PortfolioMetricsDto metrics) {
    List<DcaEntry> sellOps = dcaEntries.stream()
        .filter(e -> "SELL".equals(e.getType()))
        .sorted(Comparator.comparing(DcaEntry::getDate))
        .toList();

    sb.append("## 6. Resumen de Ventas por Ticker\n\n");

    if (sellOps.isEmpty()) {
      sb.append("*Sin operaciones de venta registradas.*\n\n");
      return;
    }

    sb.append(String.format("Total de ventas: **%d** operaciones\n\n", sellOps.size()));
    sb.append("| Ticker | Nº Ventas | Total Vendido (€) | P&L Total Realizado (€) | Resultado |\n");
    sb.append(
        "|--------|-----------|--------------------|-------------------------|-----------|\n");

    Map<String, List<DcaEntry>> sellsByTicker = sellOps.stream()
        .collect(Collectors.groupingBy(DcaEntry::getTicker));

    for (Map.Entry<String, List<DcaEntry>> entry : sellsByTicker.entrySet()) {
      String ticker = entry.getKey();
      List<DcaEntry> sells = entry.getValue();

      double totalSold = sells.stream().mapToDouble(e -> e.getShares() * e.getPrice()).sum();
      // P&L FIFO canónico desde PortfolioMetricsService (misma fuente que el Resumen Ejecutivo)
      double totalPL = metrics.positionRealizedPL() != null
          ? metrics.positionRealizedPL().getOrDefault(ticker, 0.0) : 0.0;
      String result = totalPL >= 0 ? "✅ Ganancia" : "❌ Pérdida";

      sb.append(String.format("| **%s** | %d | %s | %s | %s |\n",
          ticker, sells.size(), fmtEur(totalSold), fmtEur(totalPL), result));
    }
    sb.append("\n");
  }

  // ───────────────────── DETALLE OPERATIVO ─────────────────────

  private void appendOperationalDetail(StringBuilder sb, List<Position> positions,
      Map<String, PositionDetail> detailMap) {
    sb.append("## 7. Detalle Operativo por Posición\n\n");

    boolean hasAny = positions.stream().anyMatch(p -> detailMap.containsKey(p.getTicker()));
    if (!hasAny) {
      sb.append("*Sin detalles operativos configurados para ninguna posición.*\n\n");
      return;
    }

    for (Position p : positions) {
      PositionDetail d = detailMap.get(p.getTicker());
      if (d == null) {
        continue;
      }

      boolean hasContent = d.getNotes() != null || d.getTargetWeightPct() != null ||
          d.getAlertPriceAbove() != null || d.getAlertPriceBelow() != null;
      if (!hasContent) {
        continue;
      }

      sb.append(String.format("### %s — %s\n\n", p.getTicker(), nvl(p.getName())));

      // Tabla de configuración operativa
      sb.append("| Parámetro | Valor |\n");
      sb.append("|-----------|-------|\n");

      if (d.getRiskRating() != null) {
        String risk = switch (d.getRiskRating()) {
          case "LOW" -> "🟢 Bajo";
          case "MEDIUM" -> "🟡 Medio";
          case "HIGH" -> "🔴 Alto";
          default -> d.getRiskRating();
        };
        sb.append(String.format("| **Nivel de riesgo** | %s |\n", risk));
      }
      if (d.getTargetWeightPct() != null) {
        sb.append(String.format("| **Peso objetivo** | %s |\n", fmtPct(d.getTargetWeightPct())));
      }
      if (d.getAlertPriceAbove() != null) {
        sb.append(
            String.format("| **Alerta precio superior** | %s |\n", fmtEur(d.getAlertPriceAbove())));
      }
      if (d.getAlertPriceBelow() != null) {
        sb.append(
            String.format("| **Alerta precio inferior** | %s |\n", fmtEur(d.getAlertPriceBelow())));
      }

      sb.append("\n");
    }
  }

  // ───────────────────── DISTRIBUCIÓN/ASIGNACIÓN ─────────────────────

  private void appendAllocationAnalysis(StringBuilder sb, List<Position> positions,
      Map<String, PositionDetail> detailMap, Map<String, PositionValuation> valuations,
      PositionTotals totals) {
    sb.append("## 8. Distribución de Cartera (Allocation)\n\n");

    double totalValue = totals.value();

    sb.append("### Por posición\n\n");
    sb.append("| Ticker | Valor (€) | Peso Actual (%) | Peso Objetivo (%) | Desviación (pp) |\n");
    sb.append("|--------|-----------|-----------------|--------------------|-----------------|\n");

    // Ordenar por peso descendente
    positions.stream()
        .sorted((a, b) -> Double.compare(
            valuations.get(b.getTicker()).value(), valuations.get(a.getTicker()).value()))
        .forEach(p -> {
          double val = valuations.get(p.getTicker()).value();
          double weight = totalValue > 0 ? (val / totalValue) * 100 : 0;
          PositionDetail d = detailMap.get(p.getTicker());
          String targetStr = "—";
          String deviationStr = "—";
          if (d != null && d.getTargetWeightPct() != null && d.getTargetWeightPct() > 0) {
            targetStr = fmtPct(d.getTargetWeightPct());
            double deviation = weight - d.getTargetWeightPct();
            deviationStr = fmtPct(deviation) + (Math.abs(deviation) >= 5 ? " ⚠️" : "");
          }
          sb.append(String.format("| %s | %s | %s | %s | %s |\n",
              p.getTicker(), fmtEur(val), fmtPct(weight), targetStr, deviationStr));
        });
    sb.append("\n");

    // Por sector
    Map<String, Double> sectorMap = new LinkedHashMap<>();
    for (Position p : positions) {
      String sector =
          p.getSector() != null && !p.getSector().isBlank() ? p.getSector() : "Sin clasificar";
      sectorMap.merge(sector, valuations.get(p.getTicker()).value(), Double::sum);
    }

    sb.append("### Por sector/temática\n\n");
    sb.append("| Sector | Valor (€) | Peso (%) |\n");
    sb.append("|--------|-----------|----------|\n");
    sectorMap.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .forEach(e -> {
          double weight = totalValue > 0 ? (e.getValue() / totalValue) * 100 : 0;
          sb.append(String.format("| %s | %s | %s |\n", e.getKey(), fmtEur(e.getValue()),
              fmtPct(weight)));
        });
    sb.append("\n");
  }


  private void appendPriceEvolution(StringBuilder sb, List<Position> positions) {
    sb.append("## 9. Evolución de Precios (Resumen)\n\n");

    Instant oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
    Instant oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    Instant threeMonthsAgo = Instant.now().minus(90, ChronoUnit.DAYS);

    sb.append(
        "| Ticker | Precio Actual (€) | Var. 7d (%) | Var. 30d (%) | Var. 90d (%) | Mín. 30d (€) | Máx. 30d (€) |\n");
    sb.append(
        "|--------|-------------------|-------------|--------------|--------------|--------------|---------------|\n");

    for (Position p : positions) {
      if (p.getCurrentPrice() == null) {
        sb.append(String.format("| %s | — | — | — | — | — | — |\n", p.getTicker()));
        continue;
      }

      List<PriceHistory> month = priceHistoryRepository
          .findByTickerAndTimestampAfterOrderByTimestampAsc(p.getTicker(), oneMonthAgo);
      List<PriceHistory> quarter = priceHistoryRepository
          .findByTickerAndTimestampAfterOrderByTimestampAsc(p.getTicker(), threeMonthsAgo);

      Double var7d = calcVariation(p.getCurrentPrice(), month, oneWeekAgo);
      Double var30d = calcVariation(p.getCurrentPrice(), month, oneMonthAgo);
      Double var90d = calcVariation(p.getCurrentPrice(), quarter, threeMonthsAgo);

      double min30d = month.stream().mapToDouble(PriceHistory::getPriceEur).min()
          .orElse(p.getCurrentPrice());
      double max30d = month.stream().mapToDouble(PriceHistory::getPriceEur).max()
          .orElse(p.getCurrentPrice());
      // Solo mostrar min/max si hay datos de al menos 7 días
      boolean hasEnoughMonthData = !month.isEmpty() &&
          month.getFirst().getTimestamp().isBefore(Instant.now().minus(7, ChronoUnit.DAYS));

      sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s |\n",
          p.getTicker(),
          fmtNum(p.getCurrentPrice(), 4),
          var7d != null ? fmtPct(var7d) : "Sin datos",
          var30d != null ? fmtPct(var30d) : "Sin datos",
          var90d != null ? fmtPct(var90d) : "Sin datos",
          month.isEmpty() ? "Sin datos"
              : hasEnoughMonthData ? fmtNum(min30d, 4) : "Datos insuficientes",
          month.isEmpty() ? "Sin datos"
              : hasEnoughMonthData ? fmtNum(max30d, 4) : "Datos insuficientes"));
    }
    sb.append("\n");

    // Últimos puntos de precio (semanal) para dar contexto temporal
    sb.append("### Puntos de precio recientes (últimos 7 días)\n\n");

    // Recoger datos de todos los tickers para una tabla conjunta
    sb.append("| Ticker | Fecha/Hora | Precio (€) |\n");
    sb.append("|--------|------------|------------|\n");

    boolean hasAnyData = false;
    for (Position p : positions) {
      List<PriceHistory> week = priceHistoryRepository
          .findByTickerAndTimestampAfterOrderByTimestampAsc(p.getTicker(), oneWeekAgo);
      if (week.isEmpty()) {
        continue;
      }
      hasAnyData = true;

      // Tomar máximo ~5 puntos equidistantes + el último
      int step = Math.max(1, week.size() / 5);
      Set<Integer> indices = new LinkedHashSet<>();
      for (int i = 0; i < week.size(); i += step) {
        indices.add(i);
      }
      indices.add(week.size() - 1); // siempre incluir el último

      for (int idx : indices) {
        PriceHistory ph = week.get(idx);
        String ts = ph.getTimestamp().atZone(ZONE)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        sb.append(String.format("| %s | %s | %s |\n",
            p.getTicker(), ts, fmtNum(ph.getPriceEur(), 4)));
      }
    }
    if (!hasAnyData) {
      sb.append("| — | Sin datos de precio en los últimos 7 días | — |\n");
    }
    sb.append("\n");
  }

  // ───────────────────── ANÁLISIS DE RIESGO ─────────────────────

  private void appendRiskAnalysis(StringBuilder sb, List<Position> positions,
      PortfolioMetricsDto metrics, Map<String, PositionValuation> valuations,
      PositionTotals totals) {
    sb.append("## 10. Indicadores de Riesgo y Concentración\n\n");

    double totalValue = totals.value();

    // HHI (Herfindahl-Hirschman Index) para medir concentración
    double hhi = 0;
    List<Double> weights = new ArrayList<>();
    for (Position p : positions) {
      double val = valuations.get(p.getTicker()).value();
      double w = totalValue > 0 ? val / totalValue : 0;
      weights.add(w);
      hhi += w * w;
    }
    hhi *= 10000; // Escala estándar HHI

    sb.append("| Indicador | Valor | Interpretación |\n");
    sb.append("|-----------|-------|----------------|\n");
    sb.append(String.format("| **HHI (Concentración)** | %.0f | %s |\n", hhi, interpretHHI(hhi)));
    sb.append(String.format("| **Nº posiciones** | %d | %s |\n", positions.size(),
        positions.size() < 5 ? "Baja diversificación"
            : positions.size() < 10 ? "Diversificación moderada" : "Buena diversificación"));

    // Mayor concentración
    double maxWeight = weights.stream().mapToDouble(Double::doubleValue).max().orElse(0) * 100;
    sb.append(String.format("| **Mayor peso individual** | %s | %s |\n", fmtPct(maxWeight),
        maxWeight > 30 ? "⚠️ Posición dominante" : "Aceptable"));

    // Posiciones en pérdidas
    long losing = positions.stream()
        .filter(p -> p.getCurrentPrice() != null)
        .filter(p -> valuations.get(p.getTicker()).pl() < 0)
        .count();
    long withPrice = positions.stream().filter(p -> p.getCurrentPrice() != null).count();
    sb.append(String.format("| **Posiciones en pérdidas** | %d de %d | |\n", losing, withPrice));

    // Máx. drawdown individual
    Position maxDrawdown = null;
    double worstDD = 0;
    for (Position p : positions) {
      if (p.getCurrentPrice() == null) {
        continue;
      }
      double dd = ((p.getCurrentPrice() - p.getAvgPrice()) / p.getAvgPrice()) * 100;
      if (dd < worstDD) {
        worstDD = dd;
        maxDrawdown = p;
      }
    }
    if (maxDrawdown != null) {
      sb.append(String.format("| **Mayor caída vs compra** | %s (%s) | |\n",
          maxDrawdown.getTicker(), fmtPct(worstDD)));
    }

    sb.append("\n");

    // Sectores
    Map<String, Long> sectorCount = positions.stream()
        .collect(Collectors.groupingBy(
            p -> p.getSector() != null && !p.getSector().isBlank() ? p.getSector()
                : "Sin clasificar", Collectors.counting()));
    sb.append("### Diversificación sectorial\n\n");
    sectorCount.forEach((sector, count) ->
        sb.append(String.format("- **%s**: %d posiciones\n", sector, count)));
    sb.append("\n");
  }

  // ───────────────────── NOTAS DE CONTEXTO ─────────────────────

  private void appendContextNotes(StringBuilder sb, List<Position> positions) {
    sb.append("## 11. Contexto y Notas para el Análisis\n\n");
    sb.append("""
        - Todos los precios están expresados en **EUR**. Las posiciones cotizadas en otras divisas (USD, GBP, GBp)
          se convierten automáticamente usando tipos de cambio actualizados.
        - El **XIRR** (Extended Internal Rate of Return) se calcula considerando las fechas y montos exactos de cada
          compra DCA como flujos de caja negativos, y el valor actual como flujo positivo.
        - Las posiciones con XIRR "N/D" no tienen historial DCA suficiente para calcular la TIR.
        - **Comisiones:** Las compras DCA automatizadas con Trade Republic tienen comisión **0 €**. Las operaciones
          de venta tienen una comisión fija de **1 €** por operación. Estas comisiones no están descontadas
          en los cálculos de P&L del informe, pero deben tenerse en cuenta para el resultado neto real.
        - El **HHI** (Herfindahl-Hirschman Index) mide la concentración:
          - < 1500: Cartera diversificada
          - 1500-2500: Concentración moderada
          - > 2500: Alta concentración
        """);

    // Yahoo tickers para referencia
    sb.append("\n### Mapeo de tickers Yahoo Finance\n\n");
    for (Position p : positions) {
      sb.append(String.format("- `%s` → Yahoo: `%s`\n", p.getTicker(), nvl(p.getYahooTicker())));
    }
    sb.append("\n");

    sb.append("---\n\n");
    sb.append("*Informe generado automáticamente por Portfolio Tracker. ");
    sb.append("Usa este documento como contexto completo para analizar la cartera.*\n");
  }

  // ───────────────────── HELPERS ─────────────────────

  private Double calcVariation(double currentPrice, List<PriceHistory> history, Instant since) {
    Optional<PriceHistory> oldest = history.stream()
        .filter(ph -> ph.getTimestamp().isAfter(since) || ph.getTimestamp().equals(since))
        .min(Comparator.comparing(PriceHistory::getTimestamp));
    if (oldest.isEmpty()) {
      return null;
    }
    // Verificar que el punto de referencia sea realmente del periodo solicitado
    // (debe estar en la primera mitad del periodo, no ser un dato reciente)
    Instant midPoint = Instant.now().minus(
        Duration.between(since, Instant.now()).dividedBy(2));
    if (oldest.get().getTimestamp().isAfter(midPoint)) {
      return null;
    }
    double ref = oldest.get().getPriceEur();
    return ref > 0 ? ((currentPrice - ref) / ref) * 100 : null;
  }

  private String interpretHHI(double hhi) {
    if (hhi < 1500) {
      return "Cartera diversificada";
    }
    if (hhi < 2500) {
      return "Concentración moderada";
    }
    return "Alta concentración";
  }

  private String fmtEur(double v) {
    return String.format("%,.2f €", v);
  }

  private String fmtPct(double v) {
    return String.format("%+.2f%%", v);
  }

  private String fmtNum(double v, int decimals) {
    return String.format("%,." + decimals + "f", v);
  }

  private String nvl(String s) {
    return s != null && !s.isBlank() ? s : "—";
  }

  // Valoración unificada de una posición (regla única: fallback a avgPrice sin precio)
  private record PositionValuation(double invested, double value, double pl, double plPct) {}

  // Totales de cartera calculados una sola vez sobre la misma map de valoraciones
  private record PositionTotals(double invested, double value, double pl, double plPct) {}
}

