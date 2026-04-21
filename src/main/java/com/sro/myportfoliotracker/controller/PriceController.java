package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.PriceUpdateResult;
import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import com.sro.myportfoliotracker.service.PriceHistoryPurgeService;
import com.sro.myportfoliotracker.service.PriceUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

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
     * Histórico de precios de un ticker.
     * @param range rango temporal: 1d, 1w, 1m, 3m, 6m, 1y, all
     */
    @GetMapping("/history/{ticker}")
    public ResponseEntity<List<PriceHistory>> getHistory(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1m") String range) {

        Instant from = calculateFrom(range);
        List<PriceHistory> history = (from == null)
                ? priceHistoryRepository.findByTickerOrderByTimestampDesc(ticker.toUpperCase())
                : priceHistoryRepository.findByTickerAndTimestampAfterOrderByTimestampAsc(ticker.toUpperCase(), from);

        return ResponseEntity.ok(history);
    }

    /**
     * Histórico global (todas las posiciones).
     * @param range rango temporal: 1d, 1w, 1m, 3m, 6m, 1y, all
     */
    @GetMapping("/history")
    public ResponseEntity<List<PriceHistory>> getAllHistory(
            @RequestParam(defaultValue = "1m") String range) {

        Instant from = calculateFrom(range);
        List<PriceHistory> history = (from == null)
                ? priceHistoryRepository.findAll()
                : priceHistoryRepository.findByTimestampAfterOrderByTimestampAsc(from);

        return ResponseEntity.ok(history);
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
