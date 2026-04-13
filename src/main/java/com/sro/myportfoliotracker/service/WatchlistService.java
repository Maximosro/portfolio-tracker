package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.YahooQuoteExtended;
import com.sro.myportfoliotracker.model.WatchlistItem;
import com.sro.myportfoliotracker.repository.WatchlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {

    private final WatchlistItemRepository watchlistRepository;
    private final YahooFinanceService yahooFinanceService;
    private final ExchangeRateService exchangeRateService;

    /** Solo actualizar precios si han pasado más de 20 horas desde la última actualización */
    private static final Duration UPDATE_COOLDOWN = Duration.ofHours(20);

    public List<WatchlistItem> findAll() {
        return watchlistRepository.findAll();
    }

    public Optional<WatchlistItem> findById(Long id) {
        return watchlistRepository.findById(id);
    }

    /**
     * Crea un item en la watchlist y obtiene su precio actual de Yahoo Finance.
     * La obtención de precio se hace FUERA de la transacción para no bloquear SQLite.
     */
    public WatchlistItem create(WatchlistItem item) {
        item.setTicker(item.getTicker().toUpperCase().trim());

        if (watchlistRepository.existsByTickerIgnoreCase(item.getTicker())) {
            throw new IllegalArgumentException("El ticker " + item.getTicker() + " ya está en la lista de seguimiento");
        }

        if (item.getYahooTicker() == null || item.getYahooTicker().isBlank()) {
            item.setYahooTicker(item.getTicker());
        }

        item.setCreatedAt(Instant.now());

        // 1. Guardar primero sin precio para asegurar la persistencia
        WatchlistItem saved = watchlistRepository.save(item);

        // 2. Obtener precio FUERA de transacción para no bloquear SQLite
        try {
            YahooQuoteExtended quote = yahooFinanceService.fetchQuoteExtended(saved.getYahooTicker());
            double priceEur = Math.round(exchangeRateService.convertToEur(quote.price(), quote.currency()) * 10000.0) / 10000.0;
            saved.setCurrentPrice(priceEur);
            saved.setCurrency(quote.currency());
            saved.setChangePctDay(quote.changePctDay());
            saved.setChangePctWeek(quote.changePctWeek());
            saved.setChangePctMonth(quote.changePctMonth());
            saved.setLastPriceUpdate(Instant.now());
            saved = watchlistRepository.save(saved);
            log.info("✓ Watchlist: precio inicial de {} → {} EUR (1d:{}% 1w:{}% 1m:{}%)",
                    saved.getTicker(), priceEur, quote.changePctDay(), quote.changePctWeek(), quote.changePctMonth());
        } catch (Exception e) {
            log.warn("⚠ Watchlist: no se pudo obtener precio inicial de {}: {}", saved.getTicker(), e.getMessage());
        }

        return saved;
    }

    @Transactional
    public WatchlistItem update(Long id, WatchlistItem updated) {
        WatchlistItem existing = watchlistRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe item de watchlist con id: " + id));

        existing.setName(updated.getName());
        existing.setNotes(updated.getNotes());

        // Permitir cambiar yahooTicker y forzar refresco de precio
        if (updated.getYahooTicker() != null && !updated.getYahooTicker().isBlank()) {
            if (!updated.getYahooTicker().equals(existing.getYahooTicker())) {
                existing.setYahooTicker(updated.getYahooTicker());
                existing.setLastPriceUpdate(null); // forzar refresco
            }
        }

        return watchlistRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        if (!watchlistRepository.existsById(id)) {
            throw new IllegalArgumentException("No existe item de watchlist con id: " + id);
        }
        watchlistRepository.deleteById(id);
    }


    /**
     * Actualiza precios de watchlist una vez al día a las 09:00.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void scheduledWatchlistUpdate() {
        log.info("⏰ Actualización diaria programada de watchlist");
        updateWatchlistPrices();
    }

    /**
     * Actualización manual forzada (ignora cooldown).
     */
    public int forceUpdatePrices() {
        return doUpdatePrices(true);
    }

    /**
     * Actualización respetando el cooldown de 1x/día.
     */
    public int updateWatchlistPrices() {
        return doUpdatePrices(false);
    }

    private int doUpdatePrices(boolean force) {
        List<WatchlistItem> items = watchlistRepository.findAll();
        if (items.isEmpty()) {
            log.info("Watchlist vacía, nada que actualizar");
            return 0;
        }

        int updated = 0;
        Instant now = Instant.now();

        for (WatchlistItem item : items) {
            // Comprobar cooldown (solo si no es forzado)
            if (!force && item.getLastPriceUpdate() != null) {
                Duration sinceLastUpdate = Duration.between(item.getLastPriceUpdate(), now);
                if (sinceLastUpdate.compareTo(UPDATE_COOLDOWN) < 0) {
                    log.debug("Watchlist: {} actualizado hace {}, omitido (cooldown)", item.getTicker(), sinceLastUpdate);
                    continue;
                }
            }

            String yahoo = item.getYahooTicker();
            if (yahoo == null || yahoo.isBlank()) {
                log.warn("Watchlist: {} sin Yahoo Ticker, omitido", item.getTicker());
                continue;
            }

            try {
                YahooQuoteExtended quote = yahooFinanceService.fetchQuoteExtended(yahoo);
                double priceEur = Math.round(exchangeRateService.convertToEur(quote.price(), quote.currency()) * 10000.0) / 10000.0;

                // Guardar precio anterior antes de actualizar
                if (item.getCurrentPrice() != null) {
                    item.setPreviousPrice(item.getCurrentPrice());
                }

                item.setCurrentPrice(priceEur);
                item.setCurrency(quote.currency());
                item.setChangePctDay(quote.changePctDay());
                item.setChangePctWeek(quote.changePctWeek());
                item.setChangePctMonth(quote.changePctMonth());
                item.setLastPriceUpdate(now);
                watchlistRepository.save(item);
                updated++;

                log.info("✓ Watchlist: {} → {} EUR (1d:{}% 1w:{}% 1m:{}%)",
                        item.getTicker(), priceEur, quote.changePctDay(), quote.changePctWeek(), quote.changePctMonth());

                // Delay entre peticiones
                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("✗ Watchlist: {} — {}", item.getTicker(), e.getMessage());
            }
        }

        log.info("Watchlist: {} de {} actualizados", updated, items.size());
        return updated;
    }
}

