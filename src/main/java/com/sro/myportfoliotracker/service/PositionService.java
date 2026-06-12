package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionDetailRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionService {

    private final PositionRepository positionRepository;
    private final PositionDetailRepository positionDetailRepository;
    private final DcaEntryRepository dcaEntryRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final MarketScheduleService marketScheduleService;
    private final DcaService dcaService;

    public List<Position> findAll() {
        return positionRepository.findAll();
    }

    public Optional<Position> findByTicker(String ticker) {
        return positionRepository.findById(ticker.toUpperCase());
    }

    @Transactional
    public Position create(Position position) {
        position.setTicker(position.getTicker().toUpperCase());

        if (positionRepository.existsById(position.getTicker())) {
            throw new IllegalArgumentException("Ya existe una posición con el ticker: " + position.getTicker());
        }

        if (position.getTargetPct() == null) {
            position.setTargetPct(0.0);
        }

        // Guardar la posición primero para que exista antes de insertar en dca_history
        // (la FK fk_dca_history_ticker lo requiere en PostgreSQL/Supabase)
        Position saved = positionRepository.save(position);

        // Si la posición se crea con acciones iniciales, registrar una entrada DCA
        // para que sea la fuente de verdad y recalcular desde ahí.
        final boolean hasInitialPosition = position.getShares() != null && position.getShares() > 0
                && position.getAvgPrice() != null && position.getAvgPrice() > 0;

        if (hasInitialPosition) {
            final DcaEntry seed = DcaEntry.builder()
                    .ticker(position.getTicker())
                    .date(LocalDate.now())
                    .shares(position.getShares())
                    .price(position.getAvgPrice())
                    .type("BUY")
                    .build();
            dcaEntryRepository.save(seed);

            // Recalcular desde DCA para que shares y avgPrice sean consistentes
            dcaService.recalculatePositionFromDca(position.getTicker(), position);
            saved = positionRepository.save(position);
        }

        warnIfNoMarketSchedule(saved.getYahooTicker());
        return saved;
    }

    @Transactional
    public Position update(String ticker, Position updated) {
        String normalizedTicker = ticker.toUpperCase();

        Position existing = positionRepository.findById(normalizedTicker)
                .orElseThrow(() -> new IllegalArgumentException("No existe posición con ticker: " + normalizedTicker));

        existing.setName(updated.getName());
        existing.setYahooTicker(updated.getYahooTicker());
        existing.setShares(updated.getShares());
        existing.setAvgPrice(updated.getAvgPrice());
        existing.setCurrentPrice(updated.getCurrentPrice());
        existing.setColor(updated.getColor());
        existing.setTargetPct(updated.getTargetPct() != null ? updated.getTargetPct() : 0.0);
        existing.setSector(updated.getSector());

        Position saved = positionRepository.save(existing);
        warnIfNoMarketSchedule(saved.getYahooTicker());
        return saved;
    }

    @Transactional
    public void delete(String ticker) {
        String normalizedTicker = ticker.toUpperCase();

        if (!positionRepository.existsById(normalizedTicker)) {
            throw new IllegalArgumentException("No existe posición con ticker: " + normalizedTicker);
        }

        // Eliminar detalle operativo asociado si existe
        if (positionDetailRepository.existsById(normalizedTicker)) {
            positionDetailRepository.deleteById(normalizedTicker);
        }

        // Eliminar historial DCA asociado
        dcaEntryRepository.deleteAllByTicker(normalizedTicker);

        // Eliminar historial de precios asociado
        priceHistoryRepository.deleteAllByTicker(normalizedTicker);

        log.info("Eliminando posición {} con todos sus datos asociados (DCA, precios, detalle operativo)", normalizedTicker);

        positionRepository.deleteById(normalizedTicker);
    }

    /**
     * Avisa en log si el yahooTicker tiene un sufijo sin horario de mercado configurado.
     * En ese caso se consultará Yahoo 24/7 (sin optimización).
     */
    private void warnIfNoMarketSchedule(String yahooTicker) {
        if (yahooTicker == null || yahooTicker.isBlank()) return;
        String suffix = marketScheduleService.extractSuffix(yahooTicker);
        if (!marketScheduleService.hasScheduleFor(suffix)) {
            String suffixDisplay = suffix != null ? suffix : "(sin sufijo / US)";
            log.warn("⚠ El sufijo {} del ticker {} no tiene horario de mercado configurado. Se consultará 24/7.",
                    suffixDisplay, yahooTicker);
        }
    }
}

