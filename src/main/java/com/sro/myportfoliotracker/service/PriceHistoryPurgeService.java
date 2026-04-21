package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Servicio de compactación del histórico de precios.
 *
 * Estrategia de retención:
 *   < 7 días  → todos los puntos (resolución completa, ~10 min)
 *   7-30 días → 1 punto por hora
 *   > 30 días → 1 punto por día
 *
 * Se ejecuta cada noche a las 02:00.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceHistoryPurgeService {

    private final PriceHistoryRepository priceHistoryRepository;
    private final ActivityLogService activityLog;

    /** Días con resolución completa (todos los puntos) */
    private static final int FULL_RESOLUTION_DAYS = 7;
    /** Días con resolución horaria (1 punto/hora) */
    private static final int HOURLY_RESOLUTION_DAYS = 30;

    /**
     * Ejecutar compactación cada noche a las 02:00.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledPurge() {
        log.info("🧹 Compactación programada de price_history");
        int removed = purge();
        log.info("🧹 Compactación completada: {} registros eliminados", removed);
    }

    /**
     * Ejecuta la compactación y devuelve el número de registros eliminados.
     */
    @Transactional
    public int purge() {
        Instant now = Instant.now();
        Instant hourlyBoundary = now.minus(FULL_RESOLUTION_DAYS, ChronoUnit.DAYS);
        Instant dailyBoundary = now.minus(HOURLY_RESOLUTION_DAYS, ChronoUnit.DAYS);

        List<String> tickers = priceHistoryRepository.findDistinctTickers();
        if (tickers.isEmpty()) return 0;

        int totalRemoved = 0;

        for (String ticker : tickers) {
            // 1. Compactar zona 7-30 días: mantener 1 por hora
            totalRemoved += compactRange(ticker, dailyBoundary, hourlyBoundary, ChronoUnit.HOURS);

            // 2. Compactar zona >30 días: mantener 1 por día
            Instant oldest = Instant.EPOCH;
            totalRemoved += compactRange(ticker, oldest, dailyBoundary, ChronoUnit.DAYS);
        }

        if (totalRemoved > 0) {
            activityLog.info("PURGE", "Compactación price_history: " + totalRemoved + " registros eliminados (" + tickers.size() + " tickers)", null, "🧹");
        }

        return totalRemoved;
    }

    /**
     * Para un ticker y un rango temporal, agrupa registros por la unidad temporal dada
     * (horas o días) y elimina todos excepto el último de cada grupo.
     */
    private int compactRange(String ticker, Instant from, Instant to, ChronoUnit unit) {
        List<PriceHistory> records = priceHistoryRepository
                .findByTickerAndTimestampBetweenOrderByTimestampAsc(ticker, from, to);

        if (records.size() <= 1) return 0;

        // Agrupar por bucket temporal
        Map<Long, List<PriceHistory>> buckets = new LinkedHashMap<>();
        for (PriceHistory ph : records) {
            long bucketKey = ph.getTimestamp().truncatedTo(unit).toEpochMilli();
            buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(ph);
        }

        // De cada bucket, mantener el ÚLTIMO registro (más reciente) y marcar el resto para eliminar
        List<Long> idsToDelete = new ArrayList<>();
        for (List<PriceHistory> bucket : buckets.values()) {
            if (bucket.size() <= 1) continue;
            // Mantener el último, eliminar el resto
            for (int i = 0; i < bucket.size() - 1; i++) {
                idsToDelete.add(bucket.get(i).getId());
            }
        }

        if (!idsToDelete.isEmpty()) {
            // Borrar en lotes de 500 para no saturar SQLite
            for (int i = 0; i < idsToDelete.size(); i += 500) {
                List<Long> batch = idsToDelete.subList(i, Math.min(i + 500, idsToDelete.size()));
                priceHistoryRepository.deleteAllByIdIn(batch);
            }
            log.debug("🧹 {} — eliminados {} registros (rango: {} → {}, granularidad: {})",
                    ticker, idsToDelete.size(), from, to, unit);
        }

        return idsToDelete.size();
    }

    /**
     * Devuelve estadísticas de la tabla price_history.
     */
    public Map<String, Object> getStats() {
        long total = priceHistoryRepository.count();
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long olderThanWeek = priceHistoryRepository.countByTimestampBefore(weekAgo);
        long olderThanMonth = priceHistoryRepository.countByTimestampBefore(monthAgo);

        return Map.of(
                "totalRecords", total,
                "olderThan7Days", olderThanWeek,
                "olderThan30Days", olderThanMonth,
                "recentRecords", total - olderThanWeek
        );
    }
}

