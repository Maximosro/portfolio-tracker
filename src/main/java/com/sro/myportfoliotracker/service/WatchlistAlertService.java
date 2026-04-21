package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.WatchlistAlert;
import com.sro.myportfoliotracker.model.WatchlistAlert.AlertType;
import com.sro.myportfoliotracker.model.WatchlistItem;
import com.sro.myportfoliotracker.repository.WatchlistAlertRepository;
import com.sro.myportfoliotracker.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistAlertService {

    private final WatchlistAlertRepository alertRepository;
    private final WatchlistItemRepository watchlistRepository;

    /** Cooldown de 24h antes de re-disparar la misma alerta */
    private static final long COOLDOWN_SECONDS = 24 * 3600;

    public List<WatchlistAlert> findByWatchlistItemId(Long watchlistItemId) {
        return alertRepository.findByWatchlistItemId(watchlistItemId);
    }

    public Optional<WatchlistAlert> findById(Long id) {
        return alertRepository.findById(id);
    }

    public WatchlistAlert create(WatchlistAlert alert) {
        alert.setCreatedAt(Instant.now());
        if (alert.getEnabled() == null) alert.setEnabled(true);
        if (alert.getTriggered() == null) alert.setTriggered(false);
        return alertRepository.save(alert);
    }

    public WatchlistAlert update(Long id, WatchlistAlert updated) {
        WatchlistAlert existing = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alerta no encontrada: " + id));

        // Si se proporcionan tipo/umbral válidos, actualizar
        if (updated.getAlertType() != null) {
            boolean changed = !existing.getAlertType().equals(updated.getAlertType())
                    || (updated.getThreshold() != null && updated.getThreshold() > 0 && !existing.getThreshold().equals(updated.getThreshold()));
            if (changed) {
                existing.setAlertType(updated.getAlertType());
                if (updated.getThreshold() != null && updated.getThreshold() > 0) {
                    existing.setThreshold(updated.getThreshold());
                }
                existing.setTriggered(false);
                existing.setLastTriggeredAt(null);
            }
        }
        if (updated.getEnabled() != null) {
            existing.setEnabled(updated.getEnabled());
        }
        return alertRepository.save(existing);
    }

    public void delete(Long id) {
        alertRepository.deleteById(id);
    }

    @Transactional
    public void deleteByWatchlistItemId(Long watchlistItemId) {
        alertRepository.deleteByWatchlistItemId(watchlistItemId);
    }

    /**
     * Evalúa todas las alertas habilitadas y devuelve las que se disparan.
     * Marca las disparadas con triggered=true y lastTriggeredAt.
     * Resetea triggered si la condición ya no se cumple y el cooldown ha pasado.
     */
    @Transactional
    public List<TriggeredAlert> checkAlerts() {
        List<WatchlistAlert> enabledAlerts = alertRepository.findByEnabledTrue();
        if (enabledAlerts.isEmpty()) return List.of();

        // Cargar todos los watchlist items en un mapa
        Map<Long, WatchlistItem> itemMap = watchlistRepository.findAll().stream()
                .collect(Collectors.toMap(WatchlistItem::getId, Function.identity()));

        Instant now = Instant.now();
        List<TriggeredAlert> triggered = new ArrayList<>();

        for (WatchlistAlert alert : enabledAlerts) {
            WatchlistItem item = itemMap.get(alert.getWatchlistItemId());
            if (item == null || item.getCurrentPrice() == null) continue;

            boolean conditionMet = evaluateCondition(alert, item);

            if (conditionMet) {
                // Verificar cooldown
                if (alert.getTriggered() && alert.getLastTriggeredAt() != null
                        && alert.getLastTriggeredAt().plusSeconds(COOLDOWN_SECONDS).isAfter(now)) {
                    continue; // Dentro del cooldown
                }

                alert.setTriggered(true);
                alert.setLastTriggeredAt(now);
                alertRepository.save(alert);

                triggered.add(new TriggeredAlert(alert, item, buildMessage(alert, item)));
                log.info("🔔 Watchlist alerta disparada: {} — {} (umbral: {})",
                        item.getTicker(), alert.getAlertType(), alert.getThreshold());

            } else if (alert.getTriggered()) {
                // Condición ya no se cumple → resetear si cooldown pasó
                if (alert.getLastTriggeredAt() == null
                        || alert.getLastTriggeredAt().plusSeconds(COOLDOWN_SECONDS).isBefore(now)) {
                    alert.setTriggered(false);
                    alertRepository.save(alert);
                }
            }
        }

        return triggered;
    }

    private boolean evaluateCondition(WatchlistAlert alert, WatchlistItem item) {
        return switch (alert.getAlertType()) {
            case PRICE_ABOVE -> item.getCurrentPrice() >= alert.getThreshold();
            case PRICE_BELOW -> item.getCurrentPrice() <= alert.getThreshold();
            case VOLUME_ABOVE -> {
                if (item.getVolume() == null || item.getAvgVolume() == null || item.getAvgVolume() == 0) yield false;
                yield ((double) item.getVolume() / item.getAvgVolume()) >= alert.getThreshold();
            }
            case VOLUME_BELOW -> {
                if (item.getVolume() == null || item.getAvgVolume() == null || item.getAvgVolume() == 0) yield false;
                yield ((double) item.getVolume() / item.getAvgVolume()) <= alert.getThreshold();
            }
        };
    }

    private String buildMessage(WatchlistAlert alert, WatchlistItem item) {
        return switch (alert.getAlertType()) {
            case PRICE_ABOVE -> String.format("Precio (%.2f €) ha superado %.2f €", item.getCurrentPrice(), alert.getThreshold());
            case PRICE_BELOW -> String.format("Precio (%.2f €) ha caído bajo %.2f €", item.getCurrentPrice(), alert.getThreshold());
            case VOLUME_ABOVE -> {
                double ratio = (double) item.getVolume() / item.getAvgVolume();
                yield String.format("Volumen alto: %.1fx la media (umbral: %.1fx) — Vol: %s / Avg: %s",
                        ratio, alert.getThreshold(), fmtVol(item.getVolume()), fmtVol(item.getAvgVolume()));
            }
            case VOLUME_BELOW -> {
                double ratio = (double) item.getVolume() / item.getAvgVolume();
                yield String.format("Volumen bajo: %.1fx la media (umbral: %.1fx) — Vol: %s / Avg: %s",
                        ratio, alert.getThreshold(), fmtVol(item.getVolume()), fmtVol(item.getAvgVolume()));
            }
        };
    }

    private static String fmtVol(Long v) {
        if (v == null) return "—";
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000.0);
        if (v >= 1_000) return String.format("%dK", v / 1_000);
        return v.toString();
    }

    /** Record con la alerta, el item asociado y el mensaje formateado */
    public record TriggeredAlert(WatchlistAlert alert, WatchlistItem item, String message) {}
}


