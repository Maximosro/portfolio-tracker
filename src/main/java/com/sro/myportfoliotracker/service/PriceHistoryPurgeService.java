package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de compactación del histórico de precios.
 * <p>
 * Estrategia de retención: < 1 día  (hoy)   → todos los puntos (resolución completa, ~10 min) 1-7
 * días (semana) → 1 punto por hora 7-30 días (mes)   → 1 punto por día 30-365 días (año) → 1 punto
 * por semana
 * <p>
 * Se ejecuta cada noche a las 02:00.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceHistoryPurgeService {

  private final PriceHistoryRepository priceHistoryRepository;
  private final ActivityLogService activityLog;

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
    Instant todayBoundary = now.minus(1, ChronoUnit.DAYS);
    Instant weekBoundary = now.minus(7, ChronoUnit.DAYS);
    Instant monthBoundary = now.minus(30, ChronoUnit.DAYS);
    Instant yearBoundary = now.minus(365, ChronoUnit.DAYS);

    List<String> tickers = priceHistoryRepository.findDistinctTickers();
    if (tickers.isEmpty()) {
      return 0;
    }

    int totalRemoved = 0;

    for (String ticker : tickers) {
      // 1. Semana (1-7 días): mantener 1 por hora
      totalRemoved += compactRange(ticker, weekBoundary, todayBoundary,
          ph -> ph.getTimestamp().truncatedTo(ChronoUnit.HOURS).toEpochMilli());

      // 2. Mes (7-30 días): mantener 1 por día
      totalRemoved += compactRange(ticker, monthBoundary, weekBoundary,
          ph -> ph.getTimestamp().truncatedTo(ChronoUnit.DAYS).toEpochMilli());

      // 3. Año (30-365 días): mantener 1 por semana
      totalRemoved += compactRange(ticker, yearBoundary, monthBoundary,
          ph -> {
            // Bucket semanal: año + semana ISO
            LocalDate date = ph.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
            int year = date.get(ChronoField.YEAR);
            int week = date.get(ChronoField.ALIGNED_WEEK_OF_YEAR);
            return (long) year * 100 + week;
          });
    }

    if (totalRemoved > 0) {
      activityLog.info("PURGE",
          "Compactación price_history: " + totalRemoved + " registros eliminados (" + tickers.size()
              + " tickers)", null, "🧹");
    }

    return totalRemoved;
  }

  /**
   * Para un ticker y un rango temporal, agrupa registros por bucket (definido por bucketKeyFn) y
   * elimina todos excepto el último de cada grupo.
   */
  private int compactRange(String ticker, Instant from, Instant to,
      Function<PriceHistory, Long> bucketKeyFn) {
    List<PriceHistory> records = priceHistoryRepository
        .findByTickerAndTimestampBetweenOrderByTimestampAsc(ticker, from, to);

    if (records.size() <= 1) {
      return 0;
    }

    // Agrupar por bucket temporal
    Map<Long, List<PriceHistory>> buckets = new LinkedHashMap<>();
    for (PriceHistory ph : records) {
      long bucketKey = bucketKeyFn.apply(ph);
      buckets.computeIfAbsent(bucketKey, k -> new ArrayList<>()).add(ph);
    }

    // De cada bucket, mantener el ÚLTIMO registro y eliminar el resto
    List<Long> idsToDelete = new ArrayList<>();
    for (List<PriceHistory> bucket : buckets.values()) {
      if (bucket.size() <= 1) {
        continue;
      }
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
      log.debug("🧹 {} — eliminados {} registros (rango: {} → {})",
          ticker, idsToDelete.size(), from, to);
    }

    return idsToDelete.size();
  }

  /**
   * Devuelve estadísticas de la tabla price_history.
   */
  public Map<String, Object> getStats() {
    long total = priceHistoryRepository.count();
    Instant dayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
    Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
    Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
    Instant yearAgo = Instant.now().minus(365, ChronoUnit.DAYS);

    long olderThanDay = priceHistoryRepository.countByTimestampBefore(dayAgo);
    long olderThanWeek = priceHistoryRepository.countByTimestampBefore(weekAgo);
    long olderThanMonth = priceHistoryRepository.countByTimestampBefore(monthAgo);
    long olderThanYear = priceHistoryRepository.countByTimestampBefore(yearAgo);

    return Map.of(
        "totalRecords", total,
        "today", total - olderThanDay,
        "olderThan1Day", olderThanDay,
        "olderThan7Days", olderThanWeek,
        "olderThan30Days", olderThanMonth,
        "olderThan365Days", olderThanYear
    );
  }
}

