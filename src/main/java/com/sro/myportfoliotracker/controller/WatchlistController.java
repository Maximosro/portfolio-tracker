package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.WatchlistItem;
import com.sro.myportfoliotracker.service.WatchlistService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlist")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class WatchlistController {

  private final WatchlistService watchlistService;

  @GetMapping
  public ResponseEntity<List<WatchlistItem>> getAll() {
    return ResponseEntity.ok(watchlistService.findAll());
  }

  @GetMapping("/{id}")
  public ResponseEntity<WatchlistItem> getById(@PathVariable Long id) {
    return watchlistService.findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody WatchlistItem item) {
    try {
      WatchlistItem created = watchlistService.create(item);
      return ResponseEntity.status(HttpStatus.CREATED).body(created);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", e.getMessage()));
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable Long id, @RequestBody WatchlistItem item) {
    try {
      WatchlistItem updated = watchlistService.update(id, item);
      return ResponseEntity.ok(updated);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", e.getMessage()));
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable Long id) {
    try {
      watchlistService.delete(id);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", e.getMessage()));
    }
  }

  /**
   * Fuerza la actualización de precios de todos los items de la watchlist (ignora cooldown).
   */
  @PostMapping("/refresh")
  public ResponseEntity<?> refreshPrices() {
    int updated = watchlistService.forceUpdatePrices();
    return ResponseEntity.ok(Map.of("updated", updated));
  }
}

