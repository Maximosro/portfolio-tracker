package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DcaService {

    private final DcaEntryRepository dcaEntryRepository;
    private final PositionRepository positionRepository;

    public List<DcaEntry> findAll() {
        return dcaEntryRepository.findAllByOrderByDateDesc();
    }

    public List<DcaEntry> findByTicker(String ticker) {
        return dcaEntryRepository.findByTickerOrderByDateDesc(ticker.toUpperCase());
    }

    /**
     * Registra una compra DCA y recalcula shares + avgPrice de la posición
     * desde TODOS los registros DCA para garantizar consistencia.
     */
    @Transactional
    public DcaEntry addEntry(DcaEntry entry) {
        String ticker = entry.getTicker().toUpperCase();
        entry.setTicker(ticker);

        Position position = positionRepository.findById(ticker)
                .orElseThrow(() -> new IllegalArgumentException("No existe posición con ticker: " + ticker));

        if (entry.getShares() == null || entry.getShares() <= 0) {
            throw new IllegalArgumentException("El número de acciones debe ser mayor que 0");
        }
        if (entry.getPrice() == null || entry.getPrice() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que 0");
        }
        if (entry.getDate() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }

        // Guardar primero la entrada DCA
        DcaEntry saved = dcaEntryRepository.save(entry);

        // Recalcular la posición desde TODOS los DCA (incluido el nuevo)
        recalculatePositionFromDca(ticker, position);

        return saved;
    }

    /**
     * Modifica una entrada DCA existente y recalcula los agregados de la posición.
     */
    @Transactional
    public DcaEntry updateEntry(Long id, DcaEntry updated) {
        DcaEntry existing = dcaEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe entrada DCA con id: " + id));

        if (updated.getShares() == null || updated.getShares() <= 0) {
            throw new IllegalArgumentException("El número de acciones debe ser mayor que 0");
        }
        if (updated.getPrice() == null || updated.getPrice() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor que 0");
        }
        if (updated.getDate() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }

        String ticker = existing.getTicker();

        // Actualizar campos de la entrada DCA
        existing.setShares(updated.getShares());
        existing.setPrice(updated.getPrice());
        existing.setDate(updated.getDate());
        DcaEntry saved = dcaEntryRepository.save(existing);

        // Recalcular la posición desde todos los DCA
        Position position = positionRepository.findById(ticker)
                .orElseThrow(() -> new IllegalArgumentException("No existe posición con ticker: " + ticker));
        recalculatePositionFromDca(ticker, position);

        log.info("Entrada DCA id={} actualizada para ticker {}. Posición recalculada: shares={}, avgPrice={}",
                id, ticker, position.getShares(), position.getAvgPrice());

        return saved;
    }

    /**
     * Elimina una entrada DCA y recalcula shares + avgPrice de la posición
     * desde los registros DCA restantes.
     */
    @Transactional
    public void deleteEntry(Long id) {
        DcaEntry entry = dcaEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe entrada DCA con id: " + id));

        String ticker = entry.getTicker();

        // Eliminar la entrada
        dcaEntryRepository.deleteById(id);

        // Recalcular la posición desde los DCA restantes
        Position position = positionRepository.findById(ticker)
                .orElseThrow(() -> new IllegalArgumentException("No existe posición con ticker: " + ticker));
        recalculatePositionFromDca(ticker, position);

        log.info("Entrada DCA id={} eliminada para ticker {}. Posición recalculada: shares={}, avgPrice={}",
                id, ticker, position.getShares(), position.getAvgPrice());
    }

    /**
     * Recalcula shares y avgPrice de una posición sumando TODOS los registros DCA
     * existentes para ese ticker. Si no quedan entradas DCA, pone shares=0 y avgPrice=0.
     *
     * Fórmula: avgPrice = Σ(shares_i * price_i) / Σ(shares_i)
     */
    void recalculatePositionFromDca(String ticker, Position position) {
        List<DcaEntry> allEntries = dcaEntryRepository.findByTickerOrderByDateAsc(ticker);

        if (allEntries.isEmpty()) {
            position.setShares(0.0);
            position.setAvgPrice(0.0);
            log.warn("No quedan entradas DCA para ticker {}. Posición puesta a shares=0, avgPrice=0", ticker);
        } else {
            double totalShares = allEntries.stream().mapToDouble(DcaEntry::getShares).sum();
            double totalCost = allEntries.stream().mapToDouble(e -> e.getShares() * e.getPrice()).sum();
            double avgPrice = totalShares > 0 ? totalCost / totalShares : 0.0;

            position.setShares(totalShares);
            position.setAvgPrice(avgPrice);
        }

        positionRepository.save(position);
    }
}

