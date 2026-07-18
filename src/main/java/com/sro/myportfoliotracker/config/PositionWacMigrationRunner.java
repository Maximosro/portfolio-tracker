package com.sro.myportfoliotracker.config;

import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.service.DcaService;
import com.sro.myportfoliotracker.service.WeightedAverageCalculator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Recalcula todas las posiciones y cost basis de ventas con WAC al arrancar (una sola vez).
 * <p>
 * Necesario tras migrar de FIFO a Weighted Average Cost: los avg_price y cost_basis en BD
 * corresponden al método antiguo hasta que se recalculen.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PositionWacMigrationRunner implements CommandLineRunner {

  private final PositionRepository positionRepository;
  private final DcaEntryRepository dcaEntryRepository;
  private final DcaService dcaService;

  @Override
  public void run(String... args) {
    // ── Paso 1: Recalcular posiciones ──
    List<Position> positions = positionRepository.findAll();
    int updated = 0;
    int skipped = 0;
    for (Position pos : positions) {
      if (pos.getShares() == null || pos.getShares() <= 0) {
        skipped++;
        continue;
      }
      double oldAvg = pos.getAvgPrice() != null ? pos.getAvgPrice() : 0;
      dcaService.recalculatePositionFromDca(pos.getTicker(), pos);
      double newAvg = pos.getAvgPrice() != null ? pos.getAvgPrice() : 0;
      if (Math.abs(oldAvg - newAvg) > 0.001) {
        log.info("WAC migration: {} avgPrice {} → {}", pos.getTicker(),
            String.format("%.4f", oldAvg), String.format("%.4f", newAvg));
        updated++;
      }
    }
    log.info("WAC migration (positions): {} updated, {} unchanged, {} skipped (closed)",
        updated, positions.size() - updated - skipped, skipped);

    // ── Paso 2: Recalcular costBasis de todas las ventas ──
    List<DcaEntry> allDca = dcaEntryRepository.findAllByOrderByDateDesc();
    Map<String, List<DcaEntry>> byTicker = allDca.stream()
        .collect(Collectors.groupingBy(DcaEntry::getTicker));

    int costBasisUpdated = 0;
    for (Map.Entry<String, List<DcaEntry>> tickerEntries : byTicker.entrySet()) {
      String ticker = tickerEntries.getKey();
      // Procesar en orden cronológico
      List<DcaEntry> sorted = new ArrayList<>(tickerEntries.getValue());
      sorted.sort(Comparator.comparing(DcaEntry::getDate).thenComparing(DcaEntry::getId));

      double shares = 0.0;
      double totalCost = 0.0;

      for (DcaEntry e : sorted) {
        if ("SELL".equals(e.getType())) {
          double currentAvg = shares > 0 ? totalCost / shares : 0.0;
          Double oldCostBasis = e.getCostBasis();
          if (oldCostBasis == null || Math.abs(oldCostBasis - currentAvg) > 0.001) {
            e.setCostBasis(Math.round(currentAvg * 10000.0) / 10000.0);
            dcaEntryRepository.save(e);
            costBasisUpdated++;
          }
          shares -= e.getShares();
          totalCost -= e.getShares() * currentAvg;
        } else {
          shares += e.getShares();
          totalCost += e.getShares() * e.getPrice();
        }
      }
    }
    log.info("WAC migration (costBasis): {} SELL entries updated", costBasisUpdated);
  }
}
