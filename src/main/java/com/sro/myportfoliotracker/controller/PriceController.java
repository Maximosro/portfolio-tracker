package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.PriceUpdateResult;
import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import com.sro.myportfoliotracker.service.PriceHistoryPurgeService;
import com.sro.myportfoliotracker.service.PriceUpdateService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prices")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PriceController {

  private final PriceUpdateService priceUpdateService;
  private final PriceHistoryRepository priceHistoryRepository;
  private final PriceHistoryPurgeService purgeService;

  /**
   * Dispara una actualización manual de precios.
   */
  @PostMapping("/refresh")
  public ResponseEntity<PriceUpdateResult> refresh() {
    PriceUpdateResult result = priceUpdateService.updateAllPrices();
    return ResponseEntity.ok(result);
  }

  /**
   * Devuelve el timestamp de la última actualización de precios.
   */
  @GetMapping("/last-update")
  public ResponseEntity<?> lastUpdate() {
    Instant last = priceUpdateService.getLastUpdate();
    if (last == null) {
      return ResponseEntity.ok(Map.of("lastUpdate", (Object) "never"));
    }
    return ResponseEntity.ok(Map.of("lastUpdate", last.toString()));
  }

  /**
   * Histórico de precios de un ticker. Los datos se devuelven con resolución adaptada al rango para
   * evitar gráficas ilegibles.
   */
  @GetMapping("/history/{ticker}")
  public ResponseEntity<List<PriceHistory>> getHistory(
      @PathVariable String ticker,
      @RequestParam(defaultValue = "1m") String range) {

    Instant from = calculateFrom(range);
    List<PriceHistory> history = (from == null)
        ? priceHistoryRepository.findByTickerOrderByTimestampDesc(ticker.toUpperCase())
        : priceHistoryRepository.findByTickerAndTimestampAfterOrderByTimestampAsc(
            ticker.toUpperCase(), from);

    return ResponseEntity.ok(downsample(history, range));
  }

  /**
   * Histórico global (todas las posiciones). Los datos se devuelven con resolución adaptada al
   * rango.
   */
  @GetMapping("/history")
  public ResponseEntity<List<PriceHistory>> getAllHistory(
      @RequestParam(defaultValue = "1m") String range) {

    Instant from = calculateFrom(range);
    List<PriceHistory> history = (from == null)
        ? priceHistoryRepository.findAll()
        : priceHistoryRepository.findByTimestampAfterOrderByTimestampAsc(from);

    return ResponseEntity.ok(downsample(history, range));
  }

  /**
   * Estadísticas de la tabla price_history.
   */
  @GetMapping("/history/stats")
  public ResponseEntity<Map<String, Object>> getHistoryStats() {
    return ResponseEntity.ok(purgeService.getStats());
  }

  /**
   * Ejecuta la compactación manualmente.
   */
  @PostMapping("/history/purge")
  public ResponseEntity<Map<String, Object>> purgeHistory() {
    Map<String, Object> before = purgeService.getStats();
    int removed = purgeService.purge();
    Map<String, Object> after = purgeService.getStats();
    return ResponseEntity.ok(Map.of(
        "removed", removed,
        "before", before,
        "after", after
    ));
  }

  /**
   * Downsample: reduce los puntos devueltos según el rango solicitado para que la gráfica tenga
   * densidad uniforme.
   * <p>
   * 1d              → 1 punto cada 30 min 1w              → 1 punto por hora 1m              → 1
   * punto por día 3m/6m/1y/ytd/all → 1 punto por semana
   */
  private List<PriceHistory> downsample(List<PriceHistory> data, String range) {
    if (data == null || data.size() <= 1) {
      return data;
    }

    Function<PriceHistory, Long> bucketFn = switch (range.toLowerCase()) {
      case "1d" -> null; // sin downsample, todos los puntos
      case "1w" -> ph -> ph.getTimestamp().truncatedTo(ChronoUnit.HOURS).toEpochMilli();
      case "1m" -> ph -> ph.getTimestamp().truncatedTo(ChronoUnit.DAYS).toEpochMilli();
      default -> ph -> {
        // Bucket semanal: año * 100 + semana
        LocalDate date = ph.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
        int year = date.getYear();
        int week = date.get(ChronoField.ALIGNED_WEEK_OF_YEAR);
        return (long) year * 100 + week;
      };
    };

    if (bucketFn == null) {
      return data;
    }

    // Por cada ticker+bucket, quedarnos con el ÚLTIMO punto
    Map<String, Map<Long, PriceHistory>> byTicker = new LinkedHashMap<>();
    for (PriceHistory ph : data) {
      byTicker
          .computeIfAbsent(ph.getTicker(), k -> new LinkedHashMap<>())
          .put(bucketFn.apply(ph), ph); // sobrescribe → se queda el último
    }

    // Reconstruir lista ordenada por timestamp
    List<PriceHistory> result = new ArrayList<>();
    for (Map<Long, PriceHistory> buckets : byTicker.values()) {
      result.addAll(buckets.values());
    }
    result.sort(Comparator.comparing(PriceHistory::getTimestamp));
    return result;
  }

  private Instant calculateFrom(String range) {
    Instant now = Instant.now();
    return switch (range.toLowerCase()) {
      case "1d" -> now.minus(1, ChronoUnit.DAYS);
      case "1w" -> now.minus(7, ChronoUnit.DAYS);
      case "1m" -> now.minus(30, ChronoUnit.DAYS);
      case "3m" -> now.minus(90, ChronoUnit.DAYS);
      case "6m" -> now.minus(180, ChronoUnit.DAYS);
      case "1y" -> now.minus(365, ChronoUnit.DAYS);
      case "ytd" -> LocalDate.now().withDayOfYear(1).atStartOfDay().toInstant(ZoneOffset.UTC);
      case "all" -> null;
      default -> now.minus(30, ChronoUnit.DAYS);
    };
  }
}
