package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.PriceUpdateResult;
import com.sro.myportfoliotracker.dto.YahooQuote;
import com.sro.myportfoliotracker.dto.YahooQuoteExtended;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceUpdateService {

    private final PositionRepository positionRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final YahooFinanceService yahooFinanceService;
    private final ExchangeRateService exchangeRateService;
    private final ActivityLogService activityLog;
    private final TelegramService telegramService;
    private final MarketScheduleService marketScheduleService;

    private final AtomicReference<Instant> lastUpdate = new AtomicReference<>(null);

    /** Contador de ciclos para alternar entre fetch ligero y extendido */
    private final AtomicInteger cycleCount = new AtomicInteger(0);

    /** Cada cuántos ciclos se hace fetch extendido (volumen). 3 ciclos × 10 min = 30 min */
    private static final int EXTENDED_FETCH_EVERY = 3;

    public Instant getLastUpdate() {
        return lastUpdate.get();
    }


    /** Máximo de reintentos para posiciones en ventana post-cierre */
    private static final int POST_CLOSE_MAX_RETRIES = 3;
    /** Espera entre reintentos (ms) */
    private static final long POST_CLOSE_RETRY_DELAY_MS = 60_000; // 1 minuto

    /**
     * Cada 10 minutos, 24/7.
     */
    @Scheduled(cron = "0 */10 * * * *")
    public void scheduledUpdate() {
        int cycle = cycleCount.incrementAndGet();
        boolean extended = (cycle % EXTENDED_FETCH_EVERY == 0);
        log.info("⏰ Actualización programada de precios (ciclo {}, {})", cycle, extended ? "extendido" : "ligero");
        activityLog.info("PRICE", "Actualización programada de precios iniciada" + (extended ? " (extendido)" : ""), null, "⏰");
        PriceUpdateResult result = updateAllPrices(extended);

        // Reintentar posiciones fallidas que estén en ventana post-cierre
        retryPostCloseFailures(result, extended);
    }

    /**
     * Si hay posiciones que fallaron y están en la ventana post-cierre,
     * reintentar hasta POST_CLOSE_MAX_RETRIES veces para capturar el precio de cierre.
     */
    private void retryPostCloseFailures(PriceUpdateResult result, boolean extended) {
        if (result.failedTickers() == null || result.failedTickers().isEmpty()) return;

        // Filtrar solo los fallos que están en ventana post-cierre
        List<String> postCloseFailures = result.failedTickers().stream()
                .filter(ticker -> {
                    return positionRepository.findById(ticker.replaceAll("\\s*\\(.*\\)", "").trim())
                            .map(p -> p.getYahooTicker() != null && marketScheduleService.isInPostCloseGrace(p.getYahooTicker()))
                            .orElse(false);
                })
                .toList();

        if (postCloseFailures.isEmpty()) return;

        log.info("🔄 {} posiciones en post-cierre fallaron, reintentando (máx {} intentos, 1 por minuto)",
                postCloseFailures.size(), POST_CLOSE_MAX_RETRIES);
        activityLog.info("PRICE", postCloseFailures.size() + " posiciones en post-cierre fallaron, reintentando...", null, "🔄");

        for (int attempt = 1; attempt <= POST_CLOSE_MAX_RETRIES; attempt++) {
            try {
                Thread.sleep(POST_CLOSE_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            List<String> stillFailing = new ArrayList<>();
            Instant now = Instant.now();

            for (String tickerRaw : postCloseFailures) {
                String ticker = tickerRaw.replaceAll("\\s*\\(.*\\)", "").trim();
                Position position = positionRepository.findById(ticker).orElse(null);
                if (position == null || position.getYahooTicker() == null) continue;

                try {
                    double rawPrice;
                    String currency;
                    if (extended) {
                        var quote = yahooFinanceService.fetchQuoteExtended(position.getYahooTicker());
                        rawPrice = quote.price(); currency = quote.currency();
                        position.setVolume(quote.volume()); position.setAvgVolume(quote.avgVolume());
                    } else {
                        var quote = yahooFinanceService.fetchQuote(position.getYahooTicker());
                        rawPrice = quote.price(); currency = quote.currency();
                    }
                    double priceEur = Math.round(exchangeRateService.convertToEur(rawPrice, currency) * 10000.0) / 10000.0;
                    position.setCurrentPrice(priceEur);
                    position.setLastPriceUpdate(now);
                    positionRepository.save(position);
                    priceHistoryRepository.save(PriceHistory.builder()
                            .ticker(ticker).timestamp(now).rawPrice(rawPrice).currency(currency).priceEur(priceEur).build());
                    log.info("✓ Reintento {}/{}: {} → {} EUR", attempt, POST_CLOSE_MAX_RETRIES, ticker, priceEur);
                    activityLog.success("PRICE", "Reintento post-cierre OK: " + ticker + " → " + priceEur + " EUR", ticker, "🔄");
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.warn("✗ Reintento {}/{}: {} — {}", attempt, POST_CLOSE_MAX_RETRIES, ticker, e.getMessage());
                    stillFailing.add(tickerRaw);
                }
            }

            if (stillFailing.isEmpty()) {
                log.info("✅ Todos los precios post-cierre recuperados en intento {}", attempt);
                activityLog.success("PRICE", "Precios post-cierre recuperados tras " + attempt + " reintento(s)", null, "✅");
                return;
            }
            // Para la siguiente iteración solo reintentar los que siguen fallando
            postCloseFailures = stillFailing;
        }

        log.warn("⚠ {} posiciones post-cierre no pudieron actualizarse tras {} reintentos: {}",
                postCloseFailures.size(), POST_CLOSE_MAX_RETRIES, postCloseFailures);
        activityLog.error("PRICE", postCloseFailures.size() + " posiciones post-cierre sin precio real tras reintentos: " + postCloseFailures, null, "⚠");
    }

    /**
     * A medianoche, guardar el precio actual como precio de cierre del día (previousClose).
     * Esto permite calcular la variación diaria al día siguiente.
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void snapshotClosingPrices() {
        log.info("🌙 Guardando precios de cierre del día");
        List<Position> positions = positionRepository.findAll();
        int updated = 0;
        for (Position position : positions) {
            if (position.getShares() == null || position.getShares() <= 0) continue;
            if (position.getCurrentPrice() != null && position.getCurrentPrice() > 0) {
                position.setPreviousClose(position.getCurrentPrice());
                positionRepository.save(position);
                updated++;
            }
        }
        log.info("Precios de cierre guardados: {} posiciones actualizadas", updated);
        activityLog.info("PRICE", "Precios de cierre guardados: " + updated + " posiciones", null, "🌙");
    }

    /**
     * Actualiza precios de todas las posiciones con yahooTicker definido.
     * Guarda cada precio obtenido en el histórico.
     * NO es @Transactional a propósito: cada posición se guarda independientemente
     * para que un fallo en una no impida guardar el resto.
     *
     * @param extended si true, usa fetchQuoteExtended (range=1mo, incluye volumen).
     *                 Si false, usa fetchQuote (range=1d, más ligero).
     */
    public PriceUpdateResult updateAllPrices(boolean extended) {
        return updateAllPrices(extended, false);
    }

    /**
     * @param force si true, ignora horarios de mercado (para refresh manual).
     */
    public PriceUpdateResult updateAllPrices(boolean extended, boolean force) {
        List<Position> positions = positionRepository.findAll();
        int updated = 0;
        int skippedMarketClosed = 0;
        List<String> failed = new ArrayList<>();
        Instant now = Instant.now();

        for (Position position : positions) {
            if (position.getShares() == null || position.getShares() <= 0) continue;

            String yahoo = position.getYahooTicker();
            if (yahoo == null || yahoo.isBlank()) {
                log.warn("Posición {} sin Yahoo Ticker, omitida", position.getTicker());
                failed.add(position.getTicker() + " (sin Yahoo Ticker)");
                if (position.getCurrentPrice() != null) {
                    priceHistoryRepository.save(PriceHistory.builder()
                            .ticker(position.getTicker()).timestamp(now)
                            .rawPrice(position.getCurrentPrice()).currency("EUR")
                            .priceEur(position.getCurrentPrice()).build());
                }
                continue;
            }

            // Comprobar horario de mercado (salvo refresh manual forzado)
            if (!force && !marketScheduleService.isMarketOpen(yahoo)) {
                log.debug("⏸ {} ({}) mercado cerrado — manteniendo último precio", position.getTicker(), yahoo);
                skippedMarketClosed++;
                // Guardar histórico con último precio conocido para no romper gráficas/totales
                if (position.getCurrentPrice() != null) {
                    priceHistoryRepository.save(PriceHistory.builder()
                            .ticker(position.getTicker()).timestamp(now)
                            .rawPrice(position.getCurrentPrice()).currency("EUR")
                            .priceEur(position.getCurrentPrice()).build());
                }
                continue;
            }

            try {
                double rawPrice;
                String currency;

                if (extended) {
                    YahooQuoteExtended quote = yahooFinanceService.fetchQuoteExtended(yahoo);
                    rawPrice = quote.price();
                    currency = quote.currency();
                    position.setVolume(quote.volume());
                    position.setAvgVolume(quote.avgVolume());
                } else {
                    YahooQuote quote = yahooFinanceService.fetchQuote(yahoo);
                    rawPrice = quote.price();
                    currency = quote.currency();
                    // Mantener volumen anterior (no se actualiza en ciclo ligero)
                }

                double priceEur = Math.round(exchangeRateService.convertToEur(rawPrice, currency) * 10000.0) / 10000.0;

                position.setCurrentPrice(priceEur);
                position.setLastPriceUpdate(now);
                positionRepository.save(position);
                updated++;

                priceHistoryRepository.save(PriceHistory.builder()
                        .ticker(position.getTicker()).timestamp(now)
                        .rawPrice(rawPrice).currency(currency)
                        .priceEur(priceEur).build());

                log.info("✓ {} ({}) → {} {} → {} EUR", position.getTicker(), yahoo, rawPrice, currency, priceEur);
                activityLog.success("PRICE", position.getTicker() + " → " + priceEur + " EUR (vía " + yahoo + ")", position.getTicker(), "📈");

                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("✗ {} ({}): {}", position.getTicker(), yahoo, e.getMessage());
                failed.add(position.getTicker());
                activityLog.error("PRICE", "Error actualizando " + position.getTicker() + ": " + e.getMessage(), position.getTicker(), "❌");

                if (position.getCurrentPrice() != null) {
                    priceHistoryRepository.save(PriceHistory.builder()
                            .ticker(position.getTicker()).timestamp(now)
                            .rawPrice(position.getCurrentPrice()).currency("EUR")
                            .priceEur(position.getCurrentPrice()).build());
                    log.info("↻ {} — histórico con precio existente ({} EUR)", position.getTicker(), position.getCurrentPrice());
                }
            }
        }

        lastUpdate.set(now);

        PriceUpdateResult result = new PriceUpdateResult(updated, failed.size(), now, failed);
        log.info("Actualización completada: {} OK, {} errores, {} omitidos (mercado cerrado)", updated, failed.size(), skippedMarketClosed);
        activityLog.success("PRICE", "Actualización completada: " + updated + " OK, " + failed.size() + " errores, " + skippedMarketClosed + " omitidos (mercado cerrado)", null, "✅");

        try {
            telegramService.checkAndNotifyAlerts();
        } catch (Exception e) {
            log.debug("Error revisando alertas Telegram tras actualización de precios: {}", e.getMessage());
        }

        return result;
    }

    /** Sobrecarga para compatibilidad (refresh manual = siempre extendido + forzado) */
    public PriceUpdateResult updateAllPrices() {
        return updateAllPrices(true, true);
    }
}
