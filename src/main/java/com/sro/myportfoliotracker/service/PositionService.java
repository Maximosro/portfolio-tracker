package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionDetailRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.repository.PriceHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        return positionRepository.save(position);
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

        return positionRepository.save(existing);
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
}

