package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.WatchlistAlert;
import com.sro.myportfoliotracker.service.WatchlistAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watchlist/{watchlistItemId}/alerts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class WatchlistAlertController {

    private final WatchlistAlertService alertService;

    @GetMapping
    public ResponseEntity<List<WatchlistAlert>> getAlerts(@PathVariable Long watchlistItemId) {
        return ResponseEntity.ok(alertService.findByWatchlistItemId(watchlistItemId));
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable Long watchlistItemId, @RequestBody WatchlistAlert alert) {
        try {
            alert.setWatchlistItemId(watchlistItemId);
            WatchlistAlert created = alertService.create(alert);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{alertId}")
    public ResponseEntity<?> update(@PathVariable Long watchlistItemId, @PathVariable Long alertId, @RequestBody WatchlistAlert alert) {
        try {
            alert.setWatchlistItemId(watchlistItemId);
            WatchlistAlert updated = alertService.update(alertId, alert);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<?> delete(@PathVariable Long watchlistItemId, @PathVariable Long alertId) {
        alertService.delete(alertId);
        return ResponseEntity.noContent().build();
    }
}

