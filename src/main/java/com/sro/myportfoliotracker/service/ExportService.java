package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.model.*;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.InvestmentPlanRepository;
import com.sro.myportfoliotracker.repository.PlannedCashFlowRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Genera un informe completo del portfolio en formato Markdown,
 * diseñado para ser consumido por un LLM (Claude) y poder responder
 * preguntas detalladas sobre la cartera.
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
    private final PlannedCashFlowRepository plannedCashFlowRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    public String generateReport() {
        List<Position> positions = positionRepository.findAll();
        List<DcaEntry> dcaEntries = dcaEntryRepository.findAllByOrderByDateDesc();
        PortfolioMetricsDto metrics = metricsService.calculateMetrics();
        List<PositionDetail> details = positionDetailService.findAll();
        Map<String, PositionDetail> detailMap = details.stream()
                .collect(Collectors.toMap(PositionDetail::getTicker, d -> d));
        List<AlertDto> alerts = alertService.checkAlerts();

        // Separar posiciones activas y cerradas
        List<Position> activePositions = positions.stream().filter(p -> p.getShares() > 0).toList();
        List<Position> closedPositions = positions.stream().filter(p -> p.getShares() <= 0).toList();

        StringBuilder sb = new StringBuilder();

        appendHeader(sb);
        appendExecutiveSummary(sb, positions, activePositions, closedPositions, metrics, dcaEntries);
        appendInvestmentPlan(sb, dcaEntries);
        appendPlannedCashFlows(sb);
        appendActiveAlerts(sb, alerts);
        appendPositionsDetail(sb, activePositions, metrics, dcaEntries);
        appendClosedPositionsDetail(sb, closedPositions, dcaEntries, metrics);
        appendSalesOperationsDetail(sb, dcaEntries, positions, metrics);
        appendOperationalDetail(sb, activePositions, detailMap);
        appendAllocationAnalysis(sb, activePositions, detailMap);
        appendDcaHistory(sb, dcaEntries, positions);
        appendDcaAnalytics(sb, dcaEntries, positions);
        appendPriceEvolution(sb, activePositions);
        appendRiskAnalysis(sb, activePositions, metrics);
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
                > **NOTA PARA EL ASISTENTE:** Este documento contiene un snapshot completo de una cartera de inversión real.
                > Todos los importes están en EUR salvo que se indique lo contrario. Los precios actuales provienen de
                > Yahoo Finance y se convierten automáticamente a EUR. El XIRR se calcula con los flujos DCA reales.
                > Puedes usar esta información para responder preguntas sobre rendimiento, distribución, riesgo,
                > estrategia DCA, comparativas entre posiciones, sugerencias de rebalanceo, etc.

                """);
    }

    // ───────────────────── RESUMEN EJECUTIVO ─────────────────────

    private void appendExecutiveSummary(StringBuilder sb, List<Position> positions,
                                        List<Position> activePositions, List<Position> closedPositions,
                                        PortfolioMetricsDto metrics, List<DcaEntry> dcaEntries) {
        // Solo posiciones activas para el cálculo de mercado
        double totalInvested = activePositions.stream().mapToDouble(p -> p.getShares() * p.getAvgPrice()).sum();
        double totalValue = activePositions.stream()
                .filter(p -> p.getCurrentPrice() != null)
                .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
                .sum();
        double investedPriced = activePositions.stream()
                .filter(p -> p.getCurrentPrice() != null)
                .mapToDouble(p -> p.getShares() * p.getAvgPrice())
                .sum();
        double unrealizedPL = totalValue - investedPriced;
        double unrealizedPLPct = investedPriced > 0 ? (unrealizedPL / investedPriced) * 100 : 0;
        long positionsWithPrice = activePositions.stream().filter(p -> p.getCurrentPrice() != null).count();

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
        sb.append(String.format("| **Posiciones con precio actualizado** | %d de %d activas |\n", positionsWithPrice, activePositions.size()));
        sb.append(String.format("| **Capital invertido (activas)** | %s |\n", fmtEur(totalInvested)));
        sb.append(String.format("| **Valor actual de mercado** | %s |\n", fmtEur(totalValue)));
        sb.append(String.format("| **P&L no realizado (activas)** | %s (%s) |\n", fmtEur(unrealizedPL), fmtPct(unrealizedPLPct)));
        sb.append(String.format("| **P&L realizado (ventas)** | %s %s |\n", fmtEur(totalRealizedPL),
                totalRealizedPL >= 0 ? "✅" : "❌"));
        sb.append(String.format("| **P&L total (realizado + no realizado)** | %s |\n", fmtEur(unrealizedPL + totalRealizedPL)));
        sb.append(String.format("| **Operaciones de compra** | %d |\n", totalBuyOps));
        sb.append(String.format("| **Operaciones de venta** | %d |\n", totalSellOps));
        sb.append(String.format("| **XIRR (TIR anualizada) cartera** | %s |\n",
                metrics.portfolioXirr() != null ? fmtPct(metrics.portfolioXirr() * 100) : "N/D"));
        sb.append("\n");

        // Mejor y peor posición activa
        Position best = null, worst = null;
        double bestPct = Double.NEGATIVE_INFINITY, worstPct = Double.POSITIVE_INFINITY;
        for (Position p : activePositions) {
            if (p.getCurrentPrice() == null) continue;
            double inv = p.getShares() * p.getAvgPrice();
            double val = p.getShares() * p.getCurrentPrice();
            double pct = inv > 0 ? ((val - inv) / inv) * 100 : 0;
            if (pct > bestPct) { bestPct = pct; best = p; }
            if (pct < worstPct) { worstPct = pct; worst = p; }
        }
        if (best != null) {
            sb.append(String.format("- 🏆 **Mejor posición activa:** %s (%s) → %s\n", best.getTicker(), best.getName(), fmtPct(bestPct)));
        }
        if (worst != null) {
            sb.append(String.format("- 📉 **Peor posición activa:** %s (%s) → %s\n", worst.getTicker(), worst.getName(), fmtPct(worstPct)));
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
                .filter(e -> e.getValue() != null && activePositions.stream().anyMatch(p -> p.getTicker().equals(e.getKey())))
                .sorted(Map.Entry.comparingByValue())
                .toList();

        if (!activeFirstBuys.isEmpty()) {
            Map.Entry<String, LocalDate> oldest = activeFirstBuys.getFirst();
            Map.Entry<String, LocalDate> newest = activeFirstBuys.getLast();
            long avgDays = (long) activeFirstBuys.stream()
                    .mapToLong(e -> ChronoUnit.DAYS.between(e.getValue(), LocalDate.now()))
                    .average().orElse(0);
            sb.append(String.format("- 📅 **Posición más antigua:** %s (desde %s, hace %d días)\n",
                    oldest.getKey(), oldest.getValue().format(DATE_FMT), ChronoUnit.DAYS.between(oldest.getValue(), LocalDate.now())));
            sb.append(String.format("- 🆕 **Posición más reciente:** %s (desde %s, hace %d días)\n",
                    newest.getKey(), newest.getValue().format(DATE_FMT), ChronoUnit.DAYS.between(newest.getValue(), LocalDate.now())));
            sb.append(String.format("- ⏱️ **Antigüedad media de posiciones activas:** %d días\n", avgDays));
        }
        sb.append("\n");
    }

    // ───────────────────── PLAN DE INVERSIÓN ─────────────────────

    private void appendInvestmentPlan(StringBuilder sb, List<DcaEntry> dcaEntries) {
        sb.append("## 1b. Plan de Inversión Mensual\n\n");

        InvestmentPlan plan = investmentPlanRepository.findById(1L).orElse(null);

        if (plan == null || plan.getMonthlyBudget() == null || plan.getMonthlyBudget() <= 0) {
            sb.append("⚠️ **No hay plan de inversión mensual configurado.**\n\n");
        } else {
            sb.append(String.format("| Concepto | Valor |\n"));
            sb.append("|----------|-------|\n");
            sb.append(String.format("| **Aportación mensual** | %s |\n", fmtEur(plan.getMonthlyBudget())));
            sb.append(String.format("| **Tipo** | %s |\n", plan.getBudgetType() != null ? plan.getBudgetType() : "FIJO"));
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

        // Comparar con la media real de los últimos 6 meses COMPLETOS (excluye mes actual)
        LocalDate firstDayCurrentMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate firstDayThreeMonthsAgo = firstDayCurrentMonth.minusMonths(6);
        Map<String, Double> recentMonthly = new TreeMap<>();
        for (DcaEntry e : dcaEntries) {
            if (!"SELL".equals(e.getType())
                    && !e.getDate().isBefore(firstDayThreeMonthsAgo)
                    && e.getDate().isBefore(firstDayCurrentMonth)) {
                String key = e.getDate().getYear() + "-" + String.format("%02d", e.getDate().getMonthValue());
                recentMonthly.merge(key, e.getShares() * e.getPrice(), Double::sum);
            }
        }

        if (!recentMonthly.isEmpty()) {
            double avgMonthly = recentMonthly.values().stream().mapToDouble(v -> v).average().orElse(0);
            sb.append("### Inversión real últimos 6 meses completos\n\n");
            sb.append("| Mes | Invertido (€) | Nota |\n");
            sb.append("|-----|---------------|------|\n");
            recentMonthly.forEach((k, v) -> sb.append(String.format("| %s | %s | |\n", k, fmtEur(v))));
            sb.append(String.format("| **Media mensual** | **%s** | Solo meses completos |\n", fmtEur(avgMonthly)));

            // Mostrar mes actual (parcial) como referencia
            String currentMonthKey = firstDayCurrentMonth.getYear() + "-" + String.format("%02d", firstDayCurrentMonth.getMonthValue());
            double currentMonthTotal = dcaEntries.stream()
                    .filter(e -> !"SELL".equals(e.getType())
                            && !e.getDate().isBefore(firstDayCurrentMonth))
                    .mapToDouble(e -> e.getShares() * e.getPrice())
                    .sum();
            if (currentMonthTotal > 0) {
                sb.append(String.format("| %s | %s | ⏳ Mes en curso (parcial) |\n", currentMonthKey, fmtEur(currentMonthTotal)));
            }
            sb.append("\n");

            if (plan != null && plan.getMonthlyBudget() != null && plan.getMonthlyBudget() > 0) {
                double deviation = ((avgMonthly - plan.getMonthlyBudget()) / plan.getMonthlyBudget()) * 100;
                sb.append(String.format("- Desviación media vs objetivo: **%s** %s\n\n",
                        fmtPct(deviation), Math.abs(deviation) >= 20 ? "⚠️" : "✅"));
            }
        }
    }

    // ───────────────────── FLUJOS DE CAJA PLANIFICADOS ─────────────────────

    private void appendPlannedCashFlows(StringBuilder sb) {
        List<PlannedCashFlow> pending = plannedCashFlowRepository.findByExecutedFalseOrderByExpectedDateAsc();

        sb.append("## 1c. Flujos de Caja Planificados\n\n");

        if (pending.isEmpty()) {
            sb.append("*Sin flujos de caja futuros planificados.*\n\n");
            return;
        }

        double totalPending = pending.stream().mapToDouble(PlannedCashFlow::getAmount).sum();
        sb.append(String.format("Total pendiente: **%s** en **%d** flujo(s)\n\n", fmtEur(totalPending), pending.size()));
        sb.append("| Fecha Prevista | Tipo | Importe (€) | Descripción |\n");
        sb.append("|----------------|------|-------------|-------------|\n");

        for (PlannedCashFlow cf : pending) {
            sb.append(String.format("| %s | %s | %s | %s |\n",
                    cf.getExpectedDate().format(DATE_FMT),
                    cf.getType(),
                    fmtEur(cf.getAmount()),
                    cf.getDescription()));
        }
        sb.append("\n");
    }

    // ───────────────────── ALERTAS ACTIVAS ─────────────────────

    private void appendActiveAlerts(StringBuilder sb, List<AlertDto> alerts) {
        sb.append("## 2. Alertas Activas\n\n");

        if (alerts.isEmpty()) {
            sb.append("✅ **Sin alertas activas.** Todas las posiciones están dentro de los límites configurados.\n\n");
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
                case "STOP_LOSS" -> "Stop-Loss";
                case "TAKE_PROFIT" -> "Take-Profit";
                case "TRAILING_STOP" -> "Trailing Stop";
                case "DCA_TARGET" -> "DCA Target";
                case "ALERT_ABOVE" -> "Alerta ↑";
                case "ALERT_BELOW" -> "Alerta ↓";
                case "WEIGHT_DEVIATION" -> "Peso Objetivo";
                default -> alert.getType();
            };
            sb.append(String.format("| %s | **%s** | %s | %s |\n",
                    sevIcon, alert.getTicker(), typeLabel, alert.getMessage()));
        }
        sb.append("\n");
    }

    // ───────────────────── DETALLE POR POSICIÓN ─────────────────────

    private void appendPositionsDetail(StringBuilder sb, List<Position> positions, PortfolioMetricsDto metrics, List<DcaEntry> dcaEntries) {
        sb.append("## 3. Detalle por Posición\n\n");
        sb.append("| Ticker | Nombre | Sector | Acciones | P.Medio (€) | P.Actual (€) | P.Hace7d (€) | Var.Semana | Var. Día | Invertido (€) | Valor (€) | P&L (€) | P&L (%) | XIRR | Primera Compra |\n");
        sb.append("|--------|--------|--------|----------|-------------|---------------|--------------|------------|----------|---------------|-----------|---------|---------|------|----------------|\n");

        // Pre-calcular primera compra por ticker
        Map<String, LocalDate> firstBuyByTicker = dcaEntries.stream()
                .filter(e -> !"SELL".equals(e.getType()))
                .collect(Collectors.groupingBy(DcaEntry::getTicker,
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparing(DcaEntry::getDate)),
                                opt -> opt.map(DcaEntry::getDate).orElse(null))));

        Instant oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        double sumInvested = 0, sumValue = 0, sumPL = 0;

        for (Position p : positions) {
            double invested = p.getShares() * p.getAvgPrice();
            Double value = p.getCurrentPrice() != null ? p.getShares() * p.getCurrentPrice() : null;
            Double pl = value != null ? value - invested : null;
            Double plPct = pl != null && invested > 0 ? (pl / invested) * 100 : null;
            Double xirr = metrics.positionXirr() != null ? metrics.positionXirr().get(p.getTicker()) : null;
            String dayChange = "—";
            if (p.getCurrentPrice() != null && p.getPreviousClose() != null && p.getPreviousClose() > 0) {
                double pct = ((p.getCurrentPrice() - p.getPreviousClose()) / p.getPreviousClose()) * 100;
                dayChange = fmtPct(pct);
            }

            // Precio hace 7 días y variación semanal
            String price7dStr = "—";
            String weekChangeStr = "—";
            if (p.getCurrentPrice() != null) {
                List<PriceHistory> weekHistory = priceHistoryRepository
                        .findByTickerAndTimestampAfterOrderByTimestampAsc(p.getTicker(), oneWeekAgo);
                if (!weekHistory.isEmpty()) {
                    double price7d = weekHistory.getFirst().getPriceEur();
                    price7dStr = fmtNum(price7d, 4);
                    if (price7d > 0) {
                        double weekPct = ((p.getCurrentPrice() - price7d) / price7d) * 100;
                        weekChangeStr = fmtPct(weekPct);
                    }
                }
            }

            // Primera compra
            LocalDate firstBuy = firstBuyByTicker.get(p.getTicker());
            String firstBuyStr = firstBuy != null ? firstBuy.format(DATE_FMT) : "—";

            sb.append(String.format("| **%s** | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n",
                    p.getTicker(),
                    nvl(p.getName()),
                    nvl(p.getSector()),
                    fmtNum(p.getShares(), 6),
                    fmtNum(p.getAvgPrice(), 4),
                    p.getCurrentPrice() != null ? fmtNum(p.getCurrentPrice(), 4) : "—",
                    price7dStr,
                    weekChangeStr,
                    dayChange,
                    fmtEur(invested),
                    value != null ? fmtEur(value) : "—",
                    pl != null ? fmtEur(pl) : "—",
                    plPct != null ? fmtPct(plPct) : "—",
                    xirr != null ? fmtPct(xirr * 100) : "N/D",
                    firstBuyStr
            ));

            sumInvested += invested;
            if (value != null) sumValue += value;
            if (pl != null) sumPL += pl;
        }

        double sumPLPct = sumInvested > 0 ? (sumPL / sumInvested) * 100 : 0;
        sb.append(String.format("| **TOTAL** | | | | | | | | | **%s** | **%s** | **%s** | **%s** | **%s** | |\n",
                fmtEur(sumInvested), fmtEur(sumValue), fmtEur(sumPL), fmtPct(sumPLPct),
                metrics.portfolioXirr() != null ? fmtPct(metrics.portfolioXirr() * 100) : "N/D"));
        sb.append("\n");

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

    // ───────────────────── POSICIONES CERRADAS ─────────────────────

    private void appendClosedPositionsDetail(StringBuilder sb, List<Position> closedPositions,
                                              List<DcaEntry> dcaEntries, PortfolioMetricsDto metrics) {
        sb.append("## 3b. Posiciones Cerradas\n\n");

        if (closedPositions.isEmpty()) {
            sb.append("✅ **No hay posiciones cerradas.** Todas las posiciones están activas.\n\n");
            return;
        }

        Map<String, List<DcaEntry>> dcaByTicker = dcaEntries.stream()
                .collect(Collectors.groupingBy(DcaEntry::getTicker));

        sb.append(String.format("Total de posiciones cerradas: **%d**\n\n", closedPositions.size()));
        sb.append("| Ticker | Nombre | Sector | P.Medio Compra (€) | P.Medio Venta (€) | Total Invertido (€) | Total Recuperado (€) | P&L Realizado (€) | P&L (%) | XIRR |\n");
        sb.append("|--------|--------|--------|--------------------|--------------------|---------------------|----------------------|--------------------|---------|------|\n");

        double sumInvested = 0, sumRecovered = 0, sumRealizedPL = 0;

        for (Position p : closedPositions) {
            List<DcaEntry> tickerDca = dcaByTicker.getOrDefault(p.getTicker(), Collections.emptyList());
            List<DcaEntry> buys = tickerDca.stream().filter(e -> !"SELL".equals(e.getType())).toList();
            List<DcaEntry> sells = tickerDca.stream().filter(e -> "SELL".equals(e.getType())).toList();

            double totalBought = buys.stream().mapToDouble(e -> e.getShares() * e.getPrice()).sum();
            double totalSold = sells.stream().mapToDouble(e -> e.getShares() * e.getPrice()).sum();
            double buyShares = buys.stream().mapToDouble(DcaEntry::getShares).sum();
            double sellShares = sells.stream().mapToDouble(DcaEntry::getShares).sum();
            double avgBuyPrice = buyShares > 0 ? totalBought / buyShares : 0;
            double avgSellPrice = sellShares > 0 ? totalSold / sellShares : 0;

            double realizedPL = metrics.positionRealizedPL() != null
                    ? metrics.positionRealizedPL().getOrDefault(p.getTicker(), 0.0) : 0.0;
            double plPct = totalBought > 0 ? (realizedPL / totalBought) * 100 : 0;
            Double xirr = metrics.positionXirr() != null ? metrics.positionXirr().get(p.getTicker()) : null;

            sumInvested += totalBought;
            sumRecovered += totalSold;
            sumRealizedPL += realizedPL;

            String result = realizedPL >= 0 ? "✅" : "❌";

            sb.append(String.format("| **%s** | %s | %s | %s | %s | %s | %s | %s %s | %s | %s |\n",
                    p.getTicker(),
                    nvl(p.getName()),
                    nvl(p.getSector()),
                    fmtNum(avgBuyPrice, 4),
                    avgSellPrice > 0 ? fmtNum(avgSellPrice, 4) : "—",
                    fmtEur(totalBought),
                    fmtEur(totalSold),
                    fmtEur(realizedPL), result,
                    fmtPct(plPct),
                    xirr != null ? fmtPct(xirr * 100) : "N/D"));
        }

        // Total row
        double totalPLPct = sumInvested > 0 ? (sumRealizedPL / sumInvested) * 100 : 0;
        sb.append(String.format("| **TOTAL** | | | | | **%s** | **%s** | **%s** | **%s** | |\n",
                fmtEur(sumInvested), fmtEur(sumRecovered), fmtEur(sumRealizedPL), fmtPct(totalPLPct)));
        sb.append("\n");

        // Detail per closed position
        for (Position p : closedPositions) {
            List<DcaEntry> tickerDca = dcaByTicker.getOrDefault(p.getTicker(), Collections.emptyList());
            if (tickerDca.isEmpty()) continue;

            sb.append(String.format("#### %s — %s (cerrada)\n\n", p.getTicker(), nvl(p.getName())));
            sb.append("| Fecha | Operación | Acciones | Precio (€) | Importe (€) |\n");
            sb.append("|-------|-----------|----------|------------|-------------|\n");

            tickerDca.stream().sorted(Comparator.comparing(DcaEntry::getDate)).forEach(e -> {
                boolean isSell = "SELL".equals(e.getType());
                double cost = e.getShares() * e.getPrice();
                sb.append(String.format("| %s | %s | %s | %s | %s |\n",
                        e.getDate().format(DATE_FMT),
                        isSell ? "🔴 Venta" : "🟢 Compra",
                        fmtNum(e.getShares(), 6),
                        fmtNum(e.getPrice(), 4),
                        isSell ? "-" + fmtEur(cost) : fmtEur(cost)));
            });
            sb.append("\n");
        }
    }

    // ───────────────────── OPERACIONES DE VENTA DETALLADAS ─────────────────────

    private void appendSalesOperationsDetail(StringBuilder sb, List<DcaEntry> dcaEntries,
                                              List<Position> positions, PortfolioMetricsDto metrics) {
        List<DcaEntry> sellOps = dcaEntries.stream()
                .filter(e -> "SELL".equals(e.getType()))
                .sorted(Comparator.comparing(DcaEntry::getDate))
                .toList();

        sb.append("## 4. Detalle de Operaciones de Venta\n\n");

        if (sellOps.isEmpty()) {
            sb.append("*Sin operaciones de venta registradas.*\n\n");
            return;
        }

        Map<String, Position> posMap = positions.stream()
                .collect(Collectors.toMap(Position::getTicker, p -> p, (a, b) -> a));

        // Calcular acciones acumuladas para saber si fue venta parcial o cierre
        Map<String, Double> runningSharesForSales = new HashMap<>();
        List<DcaEntry> allSorted = dcaEntries.stream()
                .sorted(Comparator.comparing(DcaEntry::getDate).thenComparing(e -> "SELL".equals(e.getType()) ? 1 : 0))
                .toList();

        Map<Long, Double> sharesAfterOp = new HashMap<>();
        for (DcaEntry e : allSorted) {
            double current = runningSharesForSales.getOrDefault(e.getTicker(), 0.0);
            if ("SELL".equals(e.getType())) {
                current -= e.getShares();
            } else {
                current += e.getShares();
            }
            runningSharesForSales.put(e.getTicker(), current);
            if (e.getId() != null) {
                sharesAfterOp.put(e.getId(), Math.max(0, current));
            }
        }

        sb.append(String.format("Total de ventas: **%d** operaciones\n\n", sellOps.size()));
        sb.append("| Fecha | Ticker | Tipo Venta | Acciones Vendidas | P.Venta (€) | Importe (€) | P.Medio Compra (€) | P&L Realizado (€) | P&L (%) | Acc. Restantes |\n");
        sb.append("|-------|--------|------------|-------------------|-------------|-------------|---------------------|--------------------|---------|-----------------|\n");

        double totalSalesProceeds = 0;
        double totalSalesRealized = 0;

        for (DcaEntry sell : sellOps) {
            double proceeds = sell.getShares() * sell.getPrice();
            Position pos = posMap.get(sell.getTicker());
            double avgBuyPrice = pos != null && pos.getAvgPrice() != null ? pos.getAvgPrice() : 0;
            double costBasis = sell.getShares() * avgBuyPrice;
            double realizedPL = proceeds - costBasis;
            double plPct = costBasis > 0 ? (realizedPL / costBasis) * 100 : 0;

            Double sharesRemaining = sell.getId() != null ? sharesAfterOp.get(sell.getId()) : null;
            String saleType = sharesRemaining != null && sharesRemaining <= 0.000001 ? "🔒 Cierre total" : "📊 Parcial";
            String remaining = sharesRemaining != null ? fmtNum(sharesRemaining, 2) : "—";
            String result = realizedPL >= 0 ? "✅" : "❌";

            totalSalesProceeds += proceeds;
            totalSalesRealized += realizedPL;

            sb.append(String.format("| %s | **%s** | %s | %s | %s | %s | %s | %s %s | %s | %s |\n",
                    sell.getDate().format(DATE_FMT),
                    sell.getTicker(),
                    saleType,
                    fmtNum(sell.getShares(), 6),
                    fmtNum(sell.getPrice(), 4),
                    fmtEur(proceeds),
                    fmtNum(avgBuyPrice, 4),
                    fmtEur(realizedPL), result,
                    fmtPct(plPct),
                    remaining));
        }

        // Total row
        String totalResult = totalSalesRealized >= 0 ? "✅" : "❌";
        sb.append(String.format("| | **TOTAL** | | | | **%s** | | **%s** %s | | |\n",
                fmtEur(totalSalesProceeds), fmtEur(totalSalesRealized), totalResult));
        sb.append("\n");

        // Summary by ticker
        sb.append("### Resumen de ventas por ticker\n\n");
        sb.append("| Ticker | Nº Ventas | Total Vendido (€) | P&L Total Realizado (€) | Resultado |\n");
        sb.append("|--------|-----------|--------------------|-------------------------|-----------|\n");

        Map<String, List<DcaEntry>> sellsByTicker = sellOps.stream()
                .collect(Collectors.groupingBy(DcaEntry::getTicker));

        for (Map.Entry<String, List<DcaEntry>> entry : sellsByTicker.entrySet()) {
            String ticker = entry.getKey();
            List<DcaEntry> sells = entry.getValue();
            Position pos = posMap.get(ticker);
            double avgBuy = pos != null && pos.getAvgPrice() != null ? pos.getAvgPrice() : 0;

            double totalSold = sells.stream().mapToDouble(e -> e.getShares() * e.getPrice()).sum();
            double totalCostBasis = sells.stream().mapToDouble(e -> e.getShares() * avgBuy).sum();
            double totalPL = totalSold - totalCostBasis;
            String result = totalPL >= 0 ? "✅ Ganancia" : "❌ Pérdida";

            sb.append(String.format("| **%s** | %d | %s | %s | %s |\n",
                    ticker, sells.size(), fmtEur(totalSold), fmtEur(totalPL), result));
        }
        sb.append("\n");
    }

    // ───────────────────── DETALLE OPERATIVO ─────────────────────

    private void appendOperationalDetail(StringBuilder sb, List<Position> positions, Map<String, PositionDetail> detailMap) {
        sb.append("## 5. Detalle Operativo por Posición\n\n");

        boolean hasAny = positions.stream().anyMatch(p -> detailMap.containsKey(p.getTicker()));
        if (!hasAny) {
            sb.append("*Sin detalles operativos configurados para ninguna posición.*\n\n");
            return;
        }

        for (Position p : positions) {
            PositionDetail d = detailMap.get(p.getTicker());
            if (d == null) continue;

            boolean hasContent = d.getStrategy() != null || d.getStopLoss() != null ||
                    d.getTakeProfit() != null || d.getTargetWeightPct() != null;
            if (!hasContent) continue;

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
            if (d.getStrategy() != null)
                sb.append(String.format("| **Estrategia** | %s |\n", d.getStrategy()));
            if (d.getTargetWeightPct() != null)
                sb.append(String.format("| **Peso objetivo** | %s |\n", fmtPct(d.getTargetWeightPct())));
            if (d.getStopLoss() != null)
                sb.append(String.format("| **Stop-Loss** | %s |\n", fmtEur(d.getStopLoss())));
            if (d.getTakeProfit() != null)
                sb.append(String.format("| **Take-Profit** | %s |\n", fmtEur(d.getTakeProfit())));
            if (d.getTrailingStopPct() != null)
                sb.append(String.format("| **Trailing Stop** | %s |\n", fmtPct(d.getTrailingStopPct())));
            if (d.getDcaTargetPrice() != null)
                sb.append(String.format("| **Precio DCA objetivo** | %s |\n", fmtEur(d.getDcaTargetPrice())));
            if (d.getAlertPriceAbove() != null)
                sb.append(String.format("| **Alerta precio superior** | %s |\n", fmtEur(d.getAlertPriceAbove())));
            if (d.getAlertPriceBelow() != null)
                sb.append(String.format("| **Alerta precio inferior** | %s |\n", fmtEur(d.getAlertPriceBelow())));

            // Distancias actuales
            if (p.getCurrentPrice() != null && p.getCurrentPrice() > 0) {
                sb.append("\n**Distancias al precio actual (").append(fmtEur(p.getCurrentPrice())).append("):**\n");
                if (d.getStopLoss() != null) {
                    double dist = ((p.getCurrentPrice() - d.getStopLoss()) / d.getStopLoss()) * 100;
                    sb.append(String.format("- Stop-Loss: %s desde precio actual\n", fmtPct(dist)));
                }
                if (d.getTakeProfit() != null) {
                    double dist = ((d.getTakeProfit() - p.getCurrentPrice()) / p.getCurrentPrice()) * 100;
                    sb.append(String.format("- Take-Profit: %s hasta objetivo\n", fmtPct(dist)));
                }
                if (d.getDcaTargetPrice() != null) {
                    double dist = ((p.getCurrentPrice() - d.getDcaTargetPrice()) / d.getDcaTargetPrice()) * 100;
                    sb.append(String.format("- DCA Target: %s desde precio actual\n", fmtPct(dist)));
                }
            }


            sb.append("\n");
        }
    }

    // ───────────────────── DISTRIBUCIÓN/ASIGNACIÓN ─────────────────────

    private void appendAllocationAnalysis(StringBuilder sb, List<Position> positions, Map<String, PositionDetail> detailMap) {
        sb.append("## 6. Distribución de Cartera (Allocation)\n\n");

        double totalValue = positions.stream()
                .mapToDouble(p -> p.getCurrentPrice() != null ? p.getShares() * p.getCurrentPrice() : p.getShares() * p.getAvgPrice())
                .sum();

        sb.append("### Por posición\n\n");
        sb.append("| Ticker | Valor (€) | Peso Actual (%) | Peso Objetivo (%) | Desviación (pp) |\n");
        sb.append("|--------|-----------|-----------------|--------------------|-----------------|\n");

        // Ordenar por peso descendente
        positions.stream()
                .sorted((a, b) -> {
                    double va = a.getCurrentPrice() != null ? a.getShares() * a.getCurrentPrice() : a.getShares() * a.getAvgPrice();
                    double vb = b.getCurrentPrice() != null ? b.getShares() * b.getCurrentPrice() : b.getShares() * b.getAvgPrice();
                    return Double.compare(vb, va);
                })
                .forEach(p -> {
                    double val = p.getCurrentPrice() != null ? p.getShares() * p.getCurrentPrice() : p.getShares() * p.getAvgPrice();
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
            String sector = p.getSector() != null && !p.getSector().isBlank() ? p.getSector() : "Sin clasificar";
            double val = p.getCurrentPrice() != null ? p.getShares() * p.getCurrentPrice() : p.getShares() * p.getAvgPrice();
            sectorMap.merge(sector, val, Double::sum);
        }

        sb.append("### Por sector/temática\n\n");
        sb.append("| Sector | Valor (€) | Peso (%) |\n");
        sb.append("|--------|-----------|----------|\n");
        sectorMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> {
                    double weight = totalValue > 0 ? (e.getValue() / totalValue) * 100 : 0;
                    sb.append(String.format("| %s | %s | %s |\n", e.getKey(), fmtEur(e.getValue()), fmtPct(weight)));
                });
        sb.append("\n");
    }

    // ───────────────────── HISTORIAL DCA ─────────────────────

    private void appendDcaHistory(StringBuilder sb, List<DcaEntry> dcaEntries, List<Position> positions) {
        sb.append("## 7. Historial Completo de Operaciones (DCA)\n\n");

        if (dcaEntries.isEmpty()) {
            sb.append("*Sin operaciones registradas.*\n\n");
            return;
        }

        Map<String, Position> posMap = positions.stream()
                .collect(Collectors.toMap(Position::getTicker, p -> p, (a, b) -> a));

        sb.append(String.format("Total de operaciones: **%d**\n\n", dcaEntries.size()));
        sb.append("| Fecha | Ticker | Tipo | Acciones | Precio (€) | Importe (€) | P&L Realizado | Observación |\n");
        sb.append("|-------|--------|------|----------|------------|-------------|---------------|-------------|\n");

        // Ordenar por fecha para calcular estado de posición en cada venta
        List<DcaEntry> sorted = dcaEntries.stream()
                .sorted(Comparator.comparing(DcaEntry::getDate))
                .toList();

        // Calcular acciones acumuladas por ticker para determinar si una venta es parcial o cierre
        Map<String, Double> runningShares = new HashMap<>();

        double totalDcaCost = 0;
        double totalRealizedInHistory = 0;

        for (DcaEntry e : sorted) {
            double cost = e.getShares() * e.getPrice();
            boolean isSell = "SELL".equals(e.getType());

            // Track running shares
            double currentShares = runningShares.getOrDefault(e.getTicker(), 0.0);
            if (isSell) {
                currentShares -= e.getShares();
            } else {
                currentShares += e.getShares();
            }
            runningShares.put(e.getTicker(), currentShares);

            String typeIcon;
            String plStr = "—";
            String observation = "—";

            if (isSell) {
                typeIcon = "🔴 VENTA";
                totalDcaCost -= cost;

                // Calculate realized P&L for this sale using the avg buy price
                Position pos = posMap.get(e.getTicker());
                if (pos != null && pos.getAvgPrice() != null && pos.getAvgPrice() > 0) {
                    double realizedPL = e.getShares() * (e.getPrice() - pos.getAvgPrice());
                    totalRealizedInHistory += realizedPL;
                    plStr = fmtEur(realizedPL) + (realizedPL >= 0 ? " ✅" : " ❌");
                }

                // Determine if partial sale or full closure
                if (currentShares <= 0.000001) {
                    observation = "🔒 Cierre total";
                } else {
                    observation = String.format("📊 Venta parcial (quedan %s acc.)", fmtNum(Math.max(0, currentShares), 2));
                }
            } else {
                typeIcon = "🟢 COMPRA";
                totalDcaCost += cost;
                if (Math.abs(runningShares.getOrDefault(e.getTicker(), 0.0) - e.getShares()) < 0.000001) {
                    observation = "🆕 Apertura";
                } else {
                    observation = String.format("📈 Acum. %s acc.", fmtNum(currentShares, 2));
                }
            }

            sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s | %s |\n",
                    e.getDate().format(DATE_FMT),
                    e.getTicker(),
                    typeIcon,
                    fmtNum(e.getShares(), 6),
                    fmtNum(e.getPrice(), 4),
                    isSell ? "-" + fmtEur(cost) : fmtEur(cost),
                    plStr,
                    observation));
        }
        sb.append(String.format("| | | | | **NETO** | **%s** | **%s** | |\n", fmtEur(totalDcaCost),
                totalRealizedInHistory != 0 ? fmtEur(totalRealizedInHistory) : "—"));
        sb.append("\n");
    }

    // ───────────────────── ANALYTICS DCA ─────────────────────

    private void appendDcaAnalytics(StringBuilder sb, List<DcaEntry> dcaEntries, List<Position> positions) {
        if (dcaEntries.isEmpty()) return;

        sb.append("## 8. Análisis de la Estrategia DCA\n\n");

        // Agrupar por ticker
        Map<String, List<DcaEntry>> byTicker = dcaEntries.stream()
                .collect(Collectors.groupingBy(DcaEntry::getTicker));

        sb.append("| Ticker | Nº Compras | Primera Compra | Última Compra | Inversión Total DCA (€) | Precio Medio DCA (€) | Frecuencia Media |\n");
        sb.append("|--------|------------|----------------|---------------|-------------------------|---------------------|------------------|\n");

        for (Map.Entry<String, List<DcaEntry>> entry : byTicker.entrySet()) {
            String ticker = entry.getKey();
            List<DcaEntry> entries = entry.getValue().stream()
                    .sorted(Comparator.comparing(DcaEntry::getDate))
                    .toList();

            double totalCost = entries.stream().mapToDouble(e -> e.getShares() * e.getPrice()).sum();
            double totalShares = entries.stream().mapToDouble(DcaEntry::getShares).sum();
            double avgPrice = totalShares > 0 ? totalCost / totalShares : 0;

            LocalDate first = entries.getFirst().getDate();
            LocalDate last = entries.getLast().getDate();
            long daysBetween = ChronoUnit.DAYS.between(first, last);
            String frequency = entries.size() > 1
                    ? String.format("~%d días", daysBetween / (entries.size() - 1))
                    : "Única compra";

            sb.append(String.format("| %s | %d | %s | %s | %s | %s | %s |\n",
                    ticker, entries.size(),
                    first.format(DATE_FMT), last.format(DATE_FMT),
                    fmtEur(totalCost), fmtNum(avgPrice, 4), frequency));
        }
        sb.append("\n");

        // Inversión mensual (solo compras, excluyendo ventas)
        sb.append("### Inversión mensual (solo compras)\n\n");
        Map<String, Double> monthlyBuys = new TreeMap<>();
        Map<String, Double> monthlySells = new TreeMap<>();
        for (DcaEntry e : dcaEntries) {
            String key = e.getDate().getYear() + "-" + String.format("%02d", e.getDate().getMonthValue());
            if ("SELL".equals(e.getType())) {
                monthlySells.merge(key, e.getShares() * e.getPrice(), Double::sum);
            } else {
                monthlyBuys.merge(key, e.getShares() * e.getPrice(), Double::sum);
            }
        }
        // Todos los meses con actividad
        Set<String> allMonths = new TreeSet<>();
        allMonths.addAll(monthlyBuys.keySet());
        allMonths.addAll(monthlySells.keySet());
        sb.append("| Mes | Compras (€) | Ventas (€) | Neto (€) |\n");
        sb.append("|-----|-------------|------------|----------|\n");
        for (String m : allMonths) {
            double buys = monthlyBuys.getOrDefault(m, 0.0);
            double sells = monthlySells.getOrDefault(m, 0.0);
            sb.append(String.format("| %s | %s | %s | %s |\n", m, fmtEur(buys),
                    sells > 0 ? fmtEur(sells) : "—", fmtEur(buys - sells)));
        }
        sb.append("\n");
    }

    // ───────────────────── EVOLUCIÓN DE PRECIOS ─────────────────────

    private void appendPriceEvolution(StringBuilder sb, List<Position> positions) {
        sb.append("## 9. Evolución de Precios (Resumen)\n\n");

        Instant oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant oneMonthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant threeMonthsAgo = Instant.now().minus(90, ChronoUnit.DAYS);

        sb.append("| Ticker | Precio Actual (€) | Var. 7d (%) | Var. 30d (%) | Var. 90d (%) | Mín. 30d (€) | Máx. 30d (€) |\n");
        sb.append("|--------|-------------------|-------------|--------------|--------------|--------------|---------------|\n");

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

            double min30d = month.stream().mapToDouble(PriceHistory::getPriceEur).min().orElse(p.getCurrentPrice());
            double max30d = month.stream().mapToDouble(PriceHistory::getPriceEur).max().orElse(p.getCurrentPrice());
            // Solo mostrar min/max si hay datos de al menos 7 días
            boolean hasEnoughMonthData = !month.isEmpty() &&
                    month.getFirst().getTimestamp().isBefore(Instant.now().minus(7, ChronoUnit.DAYS));

            sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s |\n",
                    p.getTicker(),
                    fmtNum(p.getCurrentPrice(), 4),
                    var7d != null ? fmtPct(var7d) : "Sin datos",
                    var30d != null ? fmtPct(var30d) : "Sin datos",
                    var90d != null ? fmtPct(var90d) : "Sin datos",
                    month.isEmpty() ? "Sin datos" : hasEnoughMonthData ? fmtNum(min30d, 4) : "Datos insuficientes",
                    month.isEmpty() ? "Sin datos" : hasEnoughMonthData ? fmtNum(max30d, 4) : "Datos insuficientes"));
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
            if (week.isEmpty()) continue;
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
                String ts = ph.getTimestamp().atZone(ZONE).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
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

    private void appendRiskAnalysis(StringBuilder sb, List<Position> positions, PortfolioMetricsDto metrics) {
        sb.append("## 10. Indicadores de Riesgo y Concentración\n\n");

        double totalValue = positions.stream()
                .mapToDouble(p -> p.getCurrentPrice() != null ? p.getShares() * p.getCurrentPrice() : p.getShares() * p.getAvgPrice())
                .sum();

        // HHI (Herfindahl-Hirschman Index) para medir concentración
        double hhi = 0;
        List<Double> weights = new ArrayList<>();
        for (Position p : positions) {
            double val = p.getCurrentPrice() != null ? p.getShares() * p.getCurrentPrice() : p.getShares() * p.getAvgPrice();
            double w = totalValue > 0 ? val / totalValue : 0;
            weights.add(w);
            hhi += w * w;
        }
        hhi *= 10000; // Escala estándar HHI

        sb.append("| Indicador | Valor | Interpretación |\n");
        sb.append("|-----------|-------|----------------|\n");
        sb.append(String.format("| **HHI (Concentración)** | %.0f | %s |\n", hhi, interpretHHI(hhi)));
        sb.append(String.format("| **Nº posiciones** | %d | %s |\n", positions.size(), positions.size() < 5 ? "Baja diversificación" : positions.size() < 10 ? "Diversificación moderada" : "Buena diversificación"));

        // Mayor concentración
        double maxWeight = weights.stream().mapToDouble(Double::doubleValue).max().orElse(0) * 100;
        sb.append(String.format("| **Mayor peso individual** | %s | %s |\n", fmtPct(maxWeight), maxWeight > 30 ? "⚠️ Posición dominante" : "Aceptable"));

        // Posiciones en pérdidas
        long losing = positions.stream()
                .filter(p -> p.getCurrentPrice() != null)
                .filter(p -> p.getShares() * p.getCurrentPrice() < p.getShares() * p.getAvgPrice())
                .count();
        long withPrice = positions.stream().filter(p -> p.getCurrentPrice() != null).count();
        sb.append(String.format("| **Posiciones en pérdidas** | %d de %d | |\n", losing, withPrice));

        // Máx. drawdown individual
        Position maxDrawdown = null;
        double worstDD = 0;
        for (Position p : positions) {
            if (p.getCurrentPrice() == null) continue;
            double dd = ((p.getCurrentPrice() - p.getAvgPrice()) / p.getAvgPrice()) * 100;
            if (dd < worstDD) { worstDD = dd; maxDrawdown = p; }
        }
        if (maxDrawdown != null) {
            sb.append(String.format("| **Mayor caída vs compra** | %s (%s) | |\n",
                    maxDrawdown.getTicker(), fmtPct(worstDD)));
        }

        sb.append("\n");

        // Sectores
        Map<String, Long> sectorCount = positions.stream()
                .collect(Collectors.groupingBy(p -> p.getSector() != null && !p.getSector().isBlank() ? p.getSector() : "Sin clasificar", Collectors.counting()));
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
                - **Evolución de la estrategia:** Inicialmente se operó con acciones individuales y operaciones puntuales,
                  pero rápidamente se migró a una estrategia de **DCA (Dollar Cost Averaging)**: compras periódicas
                  y sistemáticas sin intentar hacer timing del mercado. Esta es la estrategia activa actualmente y
                  la que refleja el grueso de las posiciones abiertas.
                - Las posiciones cerradas y algunas operaciones antiguas pueden corresponder a la fase inicial
                  de acciones individuales, antes de adoptar el enfoque DCA.
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
        if (oldest.isEmpty()) return null;
        // Verificar que el punto de referencia sea realmente del periodo solicitado
        // (debe estar en la primera mitad del periodo, no ser un dato reciente)
        Instant midPoint = Instant.now().minus(
                Duration.between(since, Instant.now()).dividedBy(2));
        if (oldest.get().getTimestamp().isAfter(midPoint)) return null;
        double ref = oldest.get().getPriceEur();
        return ref > 0 ? ((currentPrice - ref) / ref) * 100 : null;
    }

    private String interpretHHI(double hhi) {
        if (hhi < 1500) return "Cartera diversificada";
        if (hhi < 2500) return "Concentración moderada";
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
}

