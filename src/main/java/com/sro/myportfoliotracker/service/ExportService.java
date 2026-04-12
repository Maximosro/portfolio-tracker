package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PositionDetail;
import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
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

        StringBuilder sb = new StringBuilder();

        appendHeader(sb);
        appendExecutiveSummary(sb, positions, metrics);
        appendActiveAlerts(sb, alerts);
        appendPositionsDetail(sb, positions, metrics);
        appendOperationalDetail(sb, positions, detailMap);
        appendAllocationAnalysis(sb, positions, detailMap);
        appendDcaHistory(sb, dcaEntries, positions);
        appendDcaAnalytics(sb, dcaEntries, positions);
        appendPriceEvolution(sb, positions);
        appendRiskAnalysis(sb, positions, metrics);
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

    private void appendExecutiveSummary(StringBuilder sb, List<Position> positions, PortfolioMetricsDto metrics) {
        double totalInvested = positions.stream().mapToDouble(p -> p.getShares() * p.getAvgPrice()).sum();
        double totalValue = positions.stream()
                .filter(p -> p.getCurrentPrice() != null)
                .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
                .sum();
        double investedPriced = positions.stream()
                .filter(p -> p.getCurrentPrice() != null)
                .mapToDouble(p -> p.getShares() * p.getAvgPrice())
                .sum();
        double totalPL = totalValue - investedPriced;
        double totalPLPct = investedPriced > 0 ? (totalPL / investedPriced) * 100 : 0;
        long positionsWithPrice = positions.stream().filter(p -> p.getCurrentPrice() != null).count();

        sb.append("## 1. Resumen Ejecutivo\n\n");
        sb.append("| Métrica | Valor |\n");
        sb.append("|---------|-------|\n");
        sb.append(String.format("| **Nº de posiciones** | %d |\n", positions.size()));
        sb.append(String.format("| **Posiciones con precio actualizado** | %d de %d |\n", positionsWithPrice, positions.size()));
        sb.append(String.format("| **Capital total invertido** | %s |\n", fmtEur(totalInvested)));
        sb.append(String.format("| **Valor actual de mercado** | %s |\n", fmtEur(totalValue)));
        sb.append(String.format("| **Ganancia/Pérdida total** | %s (%s) |\n", fmtEur(totalPL), fmtPct(totalPLPct)));
        sb.append(String.format("| **XIRR (TIR anualizada) cartera** | %s |\n",
                metrics.portfolioXirr() != null ? fmtPct(metrics.portfolioXirr() * 100) : "N/D"));
        sb.append("\n");

        // Mejor y peor posición
        Position best = null, worst = null;
        double bestPct = Double.NEGATIVE_INFINITY, worstPct = Double.POSITIVE_INFINITY;
        for (Position p : positions) {
            if (p.getCurrentPrice() == null) continue;
            double inv = p.getShares() * p.getAvgPrice();
            double val = p.getShares() * p.getCurrentPrice();
            double pct = inv > 0 ? ((val - inv) / inv) * 100 : 0;
            if (pct > bestPct) { bestPct = pct; best = p; }
            if (pct < worstPct) { worstPct = pct; worst = p; }
        }
        if (best != null) {
            sb.append(String.format("- 🏆 **Mejor posición:** %s (%s) → %s\n", best.getTicker(), best.getName(), fmtPct(bestPct)));
        }
        if (worst != null) {
            sb.append(String.format("- 📉 **Peor posición:** %s (%s) → %s\n", worst.getTicker(), worst.getName(), fmtPct(worstPct)));
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

    private void appendPositionsDetail(StringBuilder sb, List<Position> positions, PortfolioMetricsDto metrics) {
        sb.append("## 3. Detalle por Posición\n\n");
        sb.append("| Ticker | Nombre | Sector | Acciones | P.Medio (€) | P.Actual (€) | Invertido (€) | Valor (€) | P&L (€) | P&L (%) | XIRR |\n");
        sb.append("|--------|--------|--------|----------|-------------|---------------|---------------|-----------|---------|---------|------|\n");

        double sumInvested = 0, sumValue = 0, sumPL = 0;

        for (Position p : positions) {
            double invested = p.getShares() * p.getAvgPrice();
            Double value = p.getCurrentPrice() != null ? p.getShares() * p.getCurrentPrice() : null;
            Double pl = value != null ? value - invested : null;
            Double plPct = pl != null && invested > 0 ? (pl / invested) * 100 : null;
            Double xirr = metrics.positionXirr() != null ? metrics.positionXirr().get(p.getTicker()) : null;
            String lastUpdate = p.getLastPriceUpdate() != null
                    ? p.getLastPriceUpdate().atZone(ZONE).format(DATETIME_FMT)
                    : "Manual";

            sb.append(String.format("| **%s** | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n",
                    p.getTicker(),
                    nvl(p.getName()),
                    nvl(p.getSector()),
                    fmtNum(p.getShares(), 6),
                    fmtNum(p.getAvgPrice(), 4),
                    p.getCurrentPrice() != null ? fmtNum(p.getCurrentPrice(), 4) : "—",
                    fmtEur(invested),
                    value != null ? fmtEur(value) : "—",
                    pl != null ? fmtEur(pl) : "—",
                    plPct != null ? fmtPct(plPct) : "—",
                    xirr != null ? fmtPct(xirr * 100) : "N/D"
            ));

            sumInvested += invested;
            if (value != null) sumValue += value;
            if (pl != null) sumPL += pl;
        }

        double sumPLPct = sumInvested > 0 ? (sumPL / sumInvested) * 100 : 0;
        sb.append(String.format("| **TOTAL** | | | | | | **%s** | **%s** | **%s** | **%s** | **%s** |\n",
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

    // ───────────────────── DETALLE OPERATIVO ─────────────────────

    private void appendOperationalDetail(StringBuilder sb, List<Position> positions, Map<String, PositionDetail> detailMap) {
        sb.append("## 4. Detalle Operativo por Posición\n\n");

        boolean hasAny = positions.stream().anyMatch(p -> detailMap.containsKey(p.getTicker()));
        if (!hasAny) {
            sb.append("*Sin detalles operativos configurados para ninguna posición.*\n\n");
            return;
        }

        for (Position p : positions) {
            PositionDetail d = detailMap.get(p.getTicker());
            if (d == null) continue;

            boolean hasContent = d.getStrategy() != null || d.getStopLoss() != null ||
                    d.getTakeProfit() != null || d.getNotes() != null || d.getTargetWeightPct() != null;
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

            // Notas
            if (d.getNotes() != null && !d.getNotes().isBlank()) {
                sb.append("\n**Notas:**\n> ").append(d.getNotes().replace("\n", "\n> ")).append("\n");
            }

            sb.append("\n");
        }
    }

    // ───────────────────── DISTRIBUCIÓN/ASIGNACIÓN ─────────────────────

    private void appendAllocationAnalysis(StringBuilder sb, List<Position> positions, Map<String, PositionDetail> detailMap) {
        sb.append("## 5. Distribución de Cartera (Allocation)\n\n");

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
        sb.append("## 6. Historial Completo de Compras (DCA)\n\n");

        if (dcaEntries.isEmpty()) {
            sb.append("*Sin operaciones DCA registradas.*\n\n");
            return;
        }

        sb.append(String.format("Total de operaciones DCA: **%d**\n\n", dcaEntries.size()));
        sb.append("| Fecha | Ticker | Acciones | Precio (€) | Coste (€) |\n");
        sb.append("|-------|--------|----------|------------|----------|\n");

        double totalDcaCost = 0;
        for (DcaEntry e : dcaEntries) {
            double cost = e.getShares() * e.getPrice();
            totalDcaCost += cost;
            sb.append(String.format("| %s | %s | %s | %s | %s |\n",
                    e.getDate().format(DATE_FMT),
                    e.getTicker(),
                    fmtNum(e.getShares(), 6),
                    fmtNum(e.getPrice(), 4),
                    fmtEur(cost)));
        }
        sb.append(String.format("| | | | **TOTAL** | **%s** |\n", fmtEur(totalDcaCost)));
        sb.append("\n");
    }

    // ───────────────────── ANALYTICS DCA ─────────────────────

    private void appendDcaAnalytics(StringBuilder sb, List<DcaEntry> dcaEntries, List<Position> positions) {
        if (dcaEntries.isEmpty()) return;

        sb.append("## 7. Análisis de la Estrategia DCA\n\n");

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

        // Inversión mensual
        sb.append("### Inversión mensual\n\n");
        Map<String, Double> monthly = new TreeMap<>();
        for (DcaEntry e : dcaEntries) {
            String key = e.getDate().getYear() + "-" + String.format("%02d", e.getDate().getMonthValue());
            monthly.merge(key, e.getShares() * e.getPrice(), Double::sum);
        }
        sb.append("| Mes | Inversión (€) |\n");
        sb.append("|-----|---------------|\n");
        monthly.forEach((k, v) -> sb.append(String.format("| %s | %s |\n", k, fmtEur(v))));
        sb.append("\n");
    }

    // ───────────────────── EVOLUCIÓN DE PRECIOS ─────────────────────

    private void appendPriceEvolution(StringBuilder sb, List<Position> positions) {
        sb.append("## 8. Evolución de Precios (Resumen)\n\n");

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

            sb.append(String.format("| %s | %s | %s | %s | %s | %s | %s |\n",
                    p.getTicker(),
                    fmtNum(p.getCurrentPrice(), 4),
                    var7d != null ? fmtPct(var7d) : "—",
                    var30d != null ? fmtPct(var30d) : "—",
                    var90d != null ? fmtPct(var90d) : "—",
                    fmtNum(min30d, 4),
                    fmtNum(max30d, 4)));
        }
        sb.append("\n");

        // Últimos puntos de precio (semanal) para dar contexto temporal
        sb.append("### Puntos de precio recientes (últimos 7 días, muestreo)\n\n");
        for (Position p : positions) {
            List<PriceHistory> week = priceHistoryRepository
                    .findByTickerAndTimestampAfterOrderByTimestampAsc(p.getTicker(), oneWeekAgo);
            if (week.isEmpty()) continue;

            sb.append(String.format("**%s** — ", p.getTicker()));
            // Tomar máximo ~10 puntos equidistantes
            int step = Math.max(1, week.size() / 10);
            StringJoiner sj = new StringJoiner(" → ");
            for (int i = 0; i < week.size(); i += step) {
                PriceHistory ph = week.get(i);
                String ts = ph.getTimestamp().atZone(ZONE).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
                sj.add(String.format("%s: %s€", ts, fmtNum(ph.getPriceEur(), 4)));
            }
            // Siempre incluir el último
            PriceHistory last = week.getLast();
            String tsLast = last.getTimestamp().atZone(ZONE).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
            sj.add(String.format("%s: %s€", tsLast, fmtNum(last.getPriceEur(), 4)));
            sb.append(sj).append("\n");
        }
        sb.append("\n");
    }

    // ───────────────────── ANÁLISIS DE RIESGO ─────────────────────

    private void appendRiskAnalysis(StringBuilder sb, List<Position> positions, PortfolioMetricsDto metrics) {
        sb.append("## 9. Indicadores de Riesgo y Concentración\n\n");

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
        sb.append("## 10. Contexto y Notas para el Análisis\n\n");
        sb.append("""
                - Todos los precios están expresados en **EUR**. Las posiciones cotizadas en otras divisas (USD, GBP, GBp)
                  se convierten automáticamente usando tipos de cambio actualizados.
                - El **XIRR** (Extended Internal Rate of Return) se calcula considerando las fechas y montos exactos de cada
                  compra DCA como flujos de caja negativos, y el valor actual como flujo positivo.
                - Las posiciones con XIRR "N/D" no tienen historial DCA suficiente para calcular la TIR.
                - La estrategia de inversión es **DCA (Dollar Cost Averaging)**: compras periódicas sin intentar
                  hacer timing del mercado.
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

