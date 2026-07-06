package com.sro.myportfoliotracker.config;

import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.service.DcaService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Recalcula todas las posiciones con FIFO al arrancar (una sola vez).
 * <p>
 * Necesario tras migrar de coste medio simple a FIFO: los avg_price en BD
 * corresponden al método antiguo hasta que se recalcule cada posición.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PositionFifoMigrationRunner implements CommandLineRunner {

  private final PositionRepository positionRepository;
  private final DcaService dcaService;

  @Override
  public void run(String... args) {
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
        log.info("FIFO migration: {} avgPrice {} → {}", pos.getTicker(),
            String.format("%.4f", oldAvg), String.format("%.4f", newAvg));
        updated++;
      }
    }
    log.info("FIFO migration complete: {} positions updated, {} unchanged, {} skipped (closed)",
        updated, positions.size() - updated - skipped, skipped);
  }
}
