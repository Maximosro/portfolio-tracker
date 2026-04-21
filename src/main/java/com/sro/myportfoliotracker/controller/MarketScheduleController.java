package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.MarketSchedule;
import com.sro.myportfoliotracker.service.MarketScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market-schedules")
@RequiredArgsConstructor
public class MarketScheduleController {

    private final MarketScheduleService marketScheduleService;

    @GetMapping
    public List<MarketSchedule> getAll() {
        return marketScheduleService.findAll();
    }

    /**
     * Comprueba si un ticker concreto tiene su mercado abierto ahora.
     */
    @GetMapping("/check")
    public Map<String, Object> checkTicker(@RequestParam String ticker) {
        boolean open = marketScheduleService.isMarketOpen(ticker);
        String suffix = marketScheduleService.extractSuffix(ticker);
        return Map.of("ticker", ticker, "suffix", suffix != null ? suffix : "US (sin sufijo)", "marketOpen", open);
    }

    @PostMapping
    public MarketSchedule create(@RequestBody MarketSchedule schedule) {
        return marketScheduleService.save(schedule);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MarketSchedule> update(@PathVariable Long id, @RequestBody MarketSchedule schedule) {
        schedule.setId(id);
        return ResponseEntity.ok(marketScheduleService.save(schedule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        marketScheduleService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

