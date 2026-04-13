package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.PeriodHistoryDto;
import com.sro.myportfoliotracker.dto.PeriodHistoryDto.HistoryEntry;
import com.sro.myportfoliotracker.dto.PeriodReturnsDto;
import com.sro.myportfoliotracker.dto.PeriodReturnsDto.PeriodReturnEntry;
import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PortfolioSnapshot;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PortfolioSnapshotRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioSnapshotService {

    private final PortfolioSnapshotRepository snapshotRepository;
    private final PositionRepository positionRepository;
    private final DcaEntryRepository dcaEntryRepository;
    private final ActivityLogService activityLog;

    // ───────────────────── SNAPSHOT ─────────────────────

    /**
     * Toma un snapshot del portfolio para la fecha indicada.
     * Si ya existe uno para esa fecha, lo actualiza.
     */
    public PortfolioSnapshot takeSnapshot(LocalDate date) {
        List<Position> positions = positionRepository.findAll();

        double totalValue = positions.stream()
                .filter(p -> p.getCurrentPrice() != null && p.getCurrentPrice() > 0)
                .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
                .sum();

        double totalInvested = positions.stream()
                .mapToDouble(p -> p.getShares() * p.getAvgPrice())
                .sum();

        // Upsert: actualizar si ya existe para esta fecha
        PortfolioSnapshot snapshot = snapshotRepository.findByDate(date)
                .orElse(PortfolioSnapshot.builder().date(date).build());

        snapshot.setTotalValue(totalValue);
        snapshot.setTotalInvested(totalInvested);

        PortfolioSnapshot saved = snapshotRepository.save(snapshot);
        log.info("📸 Snapshot {}: valor={} €, invertido={} €",
                date, String.format("%.2f", totalValue), String.format("%.2f", totalInvested));
        activityLog.info("SNAPSHOT", "Snapshot " + date + ": valor=" + String.format("%.2f", totalValue) + " €, invertido=" + String.format("%.2f", totalInvested) + " €", null, "📸");
        return saved;
    }

    /**
     * Al arrancar, crear snapshot de hoy si no existe.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(30)
    public void onStartup() {
        LocalDate today = LocalDate.now();
        if (snapshotRepository.findByDate(today).isEmpty()) {
            log.info("📸 Creando snapshot inicial para hoy");
            takeSnapshot(today);
        }
    }

    /**
     * Cada noche a las 23:55, guardar snapshot del día.
     */
    @Scheduled(cron = "0 55 23 * * *")
    public void dailySnapshot() {
        log.info("📸 Snapshot diario programado");
        takeSnapshot(LocalDate.now());
    }

    /**
     * Devuelve los snapshots filtrados por rango.
     */
    public List<PortfolioSnapshot> getSnapshots(String range) {
        LocalDate from = calculateFrom(range);
        if (from == null) {
            return snapshotRepository.findAllByOrderByDateAsc();
        }
        return snapshotRepository.findByDateGreaterThanEqualOrderByDateAsc(from);
    }

    private LocalDate calculateFrom(String range) {
        LocalDate today = LocalDate.now();
        return switch (range != null ? range.toLowerCase() : "3m") {
            case "1m" -> today.minusMonths(1);
            case "3m" -> today.minusMonths(3);
            case "6m" -> today.minusMonths(6);
            case "1y" -> today.minusYears(1);
            case "ytd" -> today.withDayOfYear(1);
            case "all" -> null;
            default -> today.minusMonths(3);
        };
    }

    // ───────────────────── RENTABILIDAD POR PERIODOS ─────────────────────

    public PeriodReturnsDto calculateReturns() {
        List<Position> positions = positionRepository.findAll();
        LocalDate today = LocalDate.now();

        // Valor actual de la cartera
        double currentValue = positions.stream()
                .filter(p -> p.getCurrentPrice() != null && p.getCurrentPrice() > 0)
                .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
                .sum();

        Map<String, PeriodReturnEntry> periods = new LinkedHashMap<>();

        // HOY: usar previousClose
        double yesterdayValue = positions.stream()
                .filter(p -> p.getCurrentPrice() != null && p.getPreviousClose() != null && p.getPreviousClose() > 0)
                .mapToDouble(p -> p.getShares() * p.getPreviousClose())
                .sum();
        double todayContributions = dcaContributionsSince(today);
        if (yesterdayValue > 0) {
            periods.put("today", buildReturn(currentValue, yesterdayValue, todayContributions, "Hoy"));
        } else {
            periods.put("today", new PeriodReturnEntry(null, null, "Hoy"));
        }

        // SEMANA
        periods.put("week", calculatePeriodReturn(currentValue, today.minusWeeks(1), "Semana"));

        // MES
        periods.put("month", calculatePeriodReturn(currentValue, today.minusMonths(1), "Mes"));

        // TRIMESTRE
        periods.put("quarter", calculatePeriodReturn(currentValue, today.minusMonths(3), "Trimestre"));

        // YTD
        periods.put("ytd", calculatePeriodReturn(currentValue, today.withDayOfYear(1), "YTD"));

        // AÑO
        periods.put("year", calculatePeriodReturn(currentValue, today.minusYears(1), "1 Año"));

        return new PeriodReturnsDto(periods);
    }

    private PeriodReturnEntry calculatePeriodReturn(double currentValue, LocalDate startDate, String label) {
        Optional<PortfolioSnapshot> startSnapshot =
                snapshotRepository.findFirstByDateLessThanEqualOrderByDateDesc(startDate);

        if (startSnapshot.isEmpty()) {
            return new PeriodReturnEntry(null, null, label);
        }

        double startValue = startSnapshot.get().getTotalValue();
        if (startValue <= 0) {
            return new PeriodReturnEntry(null, null, label);
        }

        double contributions = dcaContributionsSince(startDate);
        return buildReturn(currentValue, startValue, contributions, label);
    }

    private PeriodReturnEntry buildReturn(double currentValue, double startValue, double contributions, String label) {
        double returnEur = currentValue - startValue - contributions;
        double returnPct = startValue > 0 ? (returnEur / startValue) * 100 : 0;
        return new PeriodReturnEntry(
                Math.round(returnPct * 100.0) / 100.0,
                Math.round(returnEur * 100.0) / 100.0,
                label
        );
    }

    /**
     * Suma neta de operaciones DCA desde una fecha (inclusive).
     * Compras suman, ventas restan.
     */
    private double dcaContributionsSince(LocalDate from) {
        List<DcaEntry> entries = dcaEntryRepository.findByDateGreaterThanEqual(from);
        return entries.stream()
                .mapToDouble(e -> {
                    double amount = e.getShares() * e.getPrice();
                    return "SELL".equals(e.getType()) ? -amount : amount;
                })
                .sum();
    }

    // ───────────────────── HISTORIAL POR PERIODO ─────────────────────

    private static final DateTimeFormatter FMT_DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", new Locale("es", "ES"));
    private static final DateTimeFormatter FMT_SHORT = DateTimeFormatter.ofPattern("dd MMM", new Locale("es", "ES"));
    private static final DateTimeFormatter FMT_MONTH = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("es", "ES"));

    /**
     * Devuelve un histórico de snapshots para el periodo indicado,
     * agrupados según convenga.
     *
     * week    → cada día de la última semana
     * month   → cada día del último mes
     * quarter → agrupado por semana (últimos 3 meses)
     * ytd     → agrupado por semana
     * year    → agrupado por mes
     *
     * Los retornos se calculan como cambio en P&L (valor - invertido),
     * excluyendo automáticamente las aportaciones DCA nuevas.
     */
    public PeriodHistoryDto getPeriodHistory(String period) {
        LocalDate today = LocalDate.now();

        return switch (period != null ? period.toLowerCase() : "week") {
            case "week" -> buildDailyHistory(today.minusWeeks(1), today, "Semana");
            case "month" -> buildDailyHistory(today.minusMonths(1), today, "Mes");
            case "quarter" -> buildWeeklyHistory(today.minusMonths(3), today, "Trimestre");
            case "ytd" -> buildWeeklyHistory(today.withDayOfYear(1), today, "YTD");
            case "year" -> buildMonthlyHistory(today.minusYears(1), today, "1 Año");
            default -> buildDailyHistory(today.minusWeeks(1), today, "Semana");
        };
    }

    /**
     * Historial con granularidad diaria — genera una entrada por cada día del rango,
     * incluso si no hay snapshot para ese día.
     * El retorno se calcula como cambio en P&L para descontar aportaciones DCA.
     */
    private PeriodHistoryDto buildDailyHistory(LocalDate from, LocalDate to, String label) {
        Optional<PortfolioSnapshot> baseSn = snapshotRepository.findFirstByDateLessThanEqualOrderByDateDesc(from.minusDays(1));
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByDateBetweenOrderByDateAsc(from, to);

        // Index snapshots by date for fast lookup
        Map<LocalDate, PortfolioSnapshot> snapshotMap = new LinkedHashMap<>();
        for (PortfolioSnapshot s : snapshots) {
            snapshotMap.put(s.getDate(), s);
        }

        double baseValue = baseSn.map(PortfolioSnapshot::getTotalValue)
                .orElse(snapshots.isEmpty() ? 0.0 : snapshots.get(0).getTotalValue());
        double baseInvested = baseSn.map(PortfolioSnapshot::getTotalInvested)
                .orElse(snapshots.isEmpty() ? 0.0 : snapshots.get(0).getTotalInvested());

        List<HistoryEntry> entries = new ArrayList<>();
        double prevValue = baseValue;
        double prevInvested = baseInvested;
        double firstValue = baseValue;
        double firstInvested = baseInvested;
        double lastKnownValue = baseValue;
        double lastKnownInvested = baseInvested;
        boolean hasAnyData = false;

        // Iterate every day in range
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            PortfolioSnapshot s = snapshotMap.get(d);
            if (s != null) {
                // Return = change in P&L → excludes DCA contributions
                double currPL = s.getTotalValue() - s.getTotalInvested();
                double prevPL = prevValue - prevInvested;
                double retEur = currPL - prevPL;
                double retPct = prevValue > 0 ? (retEur / prevValue) * 100 : 0;
                entries.add(new HistoryEntry(
                        d.toString(),
                        d.format(FMT_DAY),
                        round2(s.getTotalValue()),
                        round2(s.getTotalInvested()),
                        round2(retPct),
                        round2(retEur)
                ));
                prevValue = s.getTotalValue();
                prevInvested = s.getTotalInvested();
                lastKnownValue = s.getTotalValue();
                lastKnownInvested = s.getTotalInvested();
                hasAnyData = true;
            } else {
                // No snapshot for this day — null values
                entries.add(new HistoryEntry(
                        d.toString(),
                        d.format(FMT_DAY),
                        null, null, null, null
                ));
            }
        }

        Double totalRetPct = null;
        Double totalRetEur = null;
        if (hasAnyData && firstValue > 0) {
            // Total return = change in P&L over the whole period
            double lastPL = lastKnownValue - lastKnownInvested;
            double firstPL = firstValue - firstInvested;
            totalRetEur = round2(lastPL - firstPL);
            totalRetPct = round2((totalRetEur / firstValue) * 100);
        }

        return new PeriodHistoryDto(label.toLowerCase(), label, entries, totalRetPct, totalRetEur);
    }

    /**
     * Historial agrupado por semana — genera una entrada por cada semana (lunes)
     * del rango, incluso si no hay snapshots en esa semana.
     * El retorno se calcula como cambio en P&L para descontar aportaciones DCA.
     */
    private PeriodHistoryDto buildWeeklyHistory(LocalDate from, LocalDate to, String label) {
        Optional<PortfolioSnapshot> baseSn = snapshotRepository.findFirstByDateLessThanEqualOrderByDateDesc(from.minusDays(1));
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByDateBetweenOrderByDateAsc(from, to);

        // Group snapshots by week start (Monday) — keep last snapshot per week
        Map<LocalDate, PortfolioSnapshot> weekSnapshotMap = new LinkedHashMap<>();
        for (PortfolioSnapshot s : snapshots) {
            LocalDate weekStart = s.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weekSnapshotMap.put(weekStart, s); // last one wins
        }

        double baseValue = baseSn.map(PortfolioSnapshot::getTotalValue)
                .orElse(snapshots.isEmpty() ? 0.0 : snapshots.get(0).getTotalValue());
        double baseInvested = baseSn.map(PortfolioSnapshot::getTotalInvested)
                .orElse(snapshots.isEmpty() ? 0.0 : snapshots.get(0).getTotalInvested());

        // Generate all weeks in range
        LocalDate firstMonday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = to.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<HistoryEntry> entries = new ArrayList<>();
        double prevValue = baseValue;
        double prevInvested = baseInvested;
        double firstValue = baseValue;
        double firstInvested = baseInvested;
        double lastKnownValue = baseValue;
        double lastKnownInvested = baseInvested;
        boolean hasAnyData = false;

        for (LocalDate monday = firstMonday; !monday.isAfter(lastMonday); monday = monday.plusWeeks(1)) {
            LocalDate weekEnd = monday.plusDays(6).isAfter(to) ? to : monday.plusDays(6);
            String groupLabel = monday.format(FMT_SHORT) + " – " + weekEnd.format(FMT_SHORT);

            PortfolioSnapshot s = weekSnapshotMap.get(monday);
            if (s != null) {
                double currPL = s.getTotalValue() - s.getTotalInvested();
                double prevPL = prevValue - prevInvested;
                double retEur = currPL - prevPL;
                double retPct = prevValue > 0 ? (retEur / prevValue) * 100 : 0;
                entries.add(new HistoryEntry(
                        s.getDate().toString(),
                        groupLabel,
                        round2(s.getTotalValue()),
                        round2(s.getTotalInvested()),
                        round2(retPct),
                        round2(retEur)
                ));
                prevValue = s.getTotalValue();
                prevInvested = s.getTotalInvested();
                lastKnownValue = s.getTotalValue();
                lastKnownInvested = s.getTotalInvested();
                hasAnyData = true;
            } else {
                entries.add(new HistoryEntry(
                        monday.toString(),
                        groupLabel,
                        null, null, null, null
                ));
            }
        }

        Double totalRetPct = null;
        Double totalRetEur = null;
        if (hasAnyData && firstValue > 0) {
            double lastPL = lastKnownValue - lastKnownInvested;
            double firstPL = firstValue - firstInvested;
            totalRetEur = round2(lastPL - firstPL);
            totalRetPct = round2((totalRetEur / firstValue) * 100);
        }

        return new PeriodHistoryDto(label.toLowerCase(), label, entries, totalRetPct, totalRetEur);
    }

    /**
     * Historial agrupado por mes — genera una entrada por cada mes del rango,
     * incluso si no hay snapshots en ese mes.
     * El retorno se calcula como cambio en P&L para descontar aportaciones DCA.
     */
    private PeriodHistoryDto buildMonthlyHistory(LocalDate from, LocalDate to, String label) {
        Optional<PortfolioSnapshot> baseSn = snapshotRepository.findFirstByDateLessThanEqualOrderByDateDesc(from.minusDays(1));
        List<PortfolioSnapshot> snapshots = snapshotRepository.findByDateBetweenOrderByDateAsc(from, to);

        // Group snapshots by year-month — keep last snapshot per month
        Map<String, PortfolioSnapshot> monthSnapshotMap = new LinkedHashMap<>();
        for (PortfolioSnapshot s : snapshots) {
            String monthKey = s.getDate().getYear() + "-" + String.format("%02d", s.getDate().getMonthValue());
            monthSnapshotMap.put(monthKey, s);
        }

        double baseValue = baseSn.map(PortfolioSnapshot::getTotalValue)
                .orElse(snapshots.isEmpty() ? 0.0 : snapshots.get(0).getTotalValue());
        double baseInvested = baseSn.map(PortfolioSnapshot::getTotalInvested)
                .orElse(snapshots.isEmpty() ? 0.0 : snapshots.get(0).getTotalInvested());

        // Generate all months in range
        LocalDate firstMonth = from.withDayOfMonth(1);
        LocalDate lastMonth = to.withDayOfMonth(1);

        List<HistoryEntry> entries = new ArrayList<>();
        double prevValue = baseValue;
        double prevInvested = baseInvested;
        double firstValue = baseValue;
        double firstInvested = baseInvested;
        double lastKnownValue = baseValue;
        double lastKnownInvested = baseInvested;
        boolean hasAnyData = false;

        for (LocalDate m = firstMonth; !m.isAfter(lastMonth); m = m.plusMonths(1)) {
            String monthKey = m.getYear() + "-" + String.format("%02d", m.getMonthValue());
            String groupLabel = m.format(FMT_MONTH);
            groupLabel = groupLabel.substring(0, 1).toUpperCase() + groupLabel.substring(1);

            PortfolioSnapshot s = monthSnapshotMap.get(monthKey);
            if (s != null) {
                double currPL = s.getTotalValue() - s.getTotalInvested();
                double prevPL = prevValue - prevInvested;
                double retEur = currPL - prevPL;
                double retPct = prevValue > 0 ? (retEur / prevValue) * 100 : 0;
                entries.add(new HistoryEntry(
                        s.getDate().toString(),
                        groupLabel,
                        round2(s.getTotalValue()),
                        round2(s.getTotalInvested()),
                        round2(retPct),
                        round2(retEur)
                ));
                prevValue = s.getTotalValue();
                prevInvested = s.getTotalInvested();
                lastKnownValue = s.getTotalValue();
                lastKnownInvested = s.getTotalInvested();
                hasAnyData = true;
            } else {
                entries.add(new HistoryEntry(
                        m.toString(),
                        groupLabel,
                        null, null, null, null
                ));
            }
        }

        Double totalRetPct = null;
        Double totalRetEur = null;
        if (hasAnyData && firstValue > 0) {
            double lastPL = lastKnownValue - lastKnownInvested;
            double firstPL = firstValue - firstInvested;
            totalRetEur = round2(lastPL - firstPL);
            totalRetPct = round2((totalRetEur / firstValue) * 100);
        }

        return new PeriodHistoryDto(label.toLowerCase(), label, entries, totalRetPct, totalRetEur);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

