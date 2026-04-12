package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.PriceUpdateResult;
import com.sro.myportfoliotracker.dto.YahooQuote;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PriceHistory;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceUpdateService {

    private final PositionRepository positionRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final YahooFinanceService yahooFinanceService;
    private final ExchangeRateService exchangeRateService;

    private final AtomicReference<Instant> lastUpdate = new AtomicReference<>(null);

    public Instant getLastUpdate() {
        return lastUpdate.get();
    }

    /**
     * Al arrancar la app, actualizar precios inmediatamente.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(10)
    public void onStartup() {
        log.info("🚀 Actualización de precios al arrancar la aplicación");
        updateAllPrices();
    }

    /**
     * Cada 30 minutos, 24/7.
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void scheduledUpdate() {
        log.info("⏰ Actualización programada de precios");
        updateAllPrices();
    }

    /**
     * Actualiza precios de todas las posiciones con yahooTicker definido.
     * Guarda cada precio obtenido en el histórico.
     * NO es @Transactional a propósito: cada posición se guarda independientemente
     * para que un fallo en una no impida guardar el resto.
     */
    public PriceUpdateResult updateAllPrices() {
        List<Position> positions = positionRepository.findAll();
        int updated = 0;
        List<String> failed = new ArrayList<>();
        Instant now = Instant.now();

        for (Position position : positions) {
            String yahoo = position.getYahooTicker();
            if (yahoo == null || yahoo.isBlank()) {
                log.warn("Posición {} sin Yahoo Ticker, omitida", position.getTicker());
                failed.add(position.getTicker() + " (sin Yahoo Ticker)");
                // Guardar histórico con precio existente si lo tiene
                if (position.getCurrentPrice() != null) {
                    priceHistoryRepository.save(PriceHistory.builder()
                            .ticker(position.getTicker())
                            .timestamp(now)
                            .rawPrice(position.getCurrentPrice())
                            .currency("EUR")
                            .priceEur(position.getCurrentPrice())
                            .build());
                }
                continue;
            }

            try {
                YahooQuote quote = yahooFinanceService.fetchQuote(yahoo);
                double priceEur = Math.round(exchangeRateService.convertToEur(quote.price(), quote.currency()) * 10000.0) / 10000.0;

                position.setCurrentPrice(priceEur);
                position.setLastPriceUpdate(now);
                positionRepository.save(position);
                updated++;

                // Guardar en histórico
                priceHistoryRepository.save(PriceHistory.builder()
                        .ticker(position.getTicker())
                        .timestamp(now)
                        .rawPrice(quote.price())
                        .currency(quote.currency())
                        .priceEur(priceEur)
                        .build());

                log.info("✓ {} ({}) → {} {} → {} EUR",
                        position.getTicker(), yahoo, quote.price(), quote.currency(), priceEur);

                // Delay entre peticiones para no saturar Yahoo
                Thread.sleep(500);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("✗ {} ({}): {}", position.getTicker(), yahoo, e.getMessage());
                failed.add(position.getTicker());

                // Guardar en histórico el precio existente para no romper las gráficas
                if (position.getCurrentPrice() != null) {
                    priceHistoryRepository.save(PriceHistory.builder()
                            .ticker(position.getTicker())
                            .timestamp(now)
                            .rawPrice(position.getCurrentPrice())
                            .currency("EUR")
                            .priceEur(position.getCurrentPrice())
                            .build());
                    log.info("↻ {} — histórico con precio existente ({} EUR)", position.getTicker(), position.getCurrentPrice());
                }
            }
        }


        lastUpdate.set(now);

        PriceUpdateResult result = new PriceUpdateResult(updated, failed.size(), now, failed);
        log.info("Actualización completada: {} OK, {} errores", updated, failed.size());
        return result;
    }
}
