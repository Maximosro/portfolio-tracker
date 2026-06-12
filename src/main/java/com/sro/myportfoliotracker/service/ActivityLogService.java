package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.ActivityLogEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Service;

/**
 * Servicio de log funcional en memoria (ring buffer). Almacena las últimas MAX_ENTRIES entradas de
 * actividad de la aplicación: actualizaciones de precios, envíos de Telegram, operaciones DCA,
 * alertas, watchlist, snapshots, etc.
 */
@Service
public class ActivityLogService {

  private static final int MAX_ENTRIES = 2000;

  private final ConcurrentLinkedDeque<ActivityLogEntry> entries = new ConcurrentLinkedDeque<>();

  public void log(String category, String level, String message, String ticker, String icon) {
    ActivityLogEntry entry = ActivityLogEntry.builder()
        .timestamp(Instant.now())
        .category(category)
        .level(level)
        .message(message)
        .ticker(ticker)
        .icon(icon)
        .build();
    entries.addFirst(entry);

    // Mantener tamaño máximo
    while (entries.size() > MAX_ENTRIES) {
      entries.removeLast();
    }
  }

  // Convenience methods
  public void info(String category, String message, String ticker, String icon) {
    log(category, "INFO", message, ticker, icon);
  }

  public void success(String category, String message, String ticker, String icon) {
    log(category, "SUCCESS", message, ticker, icon);
  }

  public void warning(String category, String message, String ticker, String icon) {
    log(category, "WARNING", message, ticker, icon);
  }

  public void error(String category, String message, String ticker, String icon) {
    log(category, "ERROR", message, ticker, icon);
  }

  /**
   * Devuelve todas las entradas ordenadas por timestamp descendente (más recientes primero).
   */
  public List<ActivityLogEntry> getAll() {
    return new ArrayList<>(entries);
  }

  /**
   * Devuelve las últimas N entradas.
   */
  public List<ActivityLogEntry> getLatest(int limit) {
    List<ActivityLogEntry> all = getAll();
    return all.subList(0, Math.min(limit, all.size()));
  }

  /**
   * Devuelve entradas filtradas por categoría.
   */
  public List<ActivityLogEntry> getByCategory(String category) {
    return entries.stream()
        .filter(e -> category.equalsIgnoreCase(e.getCategory()))
        .toList();
  }

  public int size() {
    return entries.size();
  }

  public void clear() {
    entries.clear();
  }
}


