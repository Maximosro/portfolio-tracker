package com.sro.myportfoliotracker.config;

import com.sro.myportfoliotracker.repository.PositionAlertRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elimina al arrancar las alertas persistidas de tipos de límites obsoletos (stop-loss,
 * take-profit, trailing stop, DCA target). Tras la eliminación de la feature de límites,
 * estos tipos ya no se generan; las filas existentes se purgan para que no sigan
 * mostrándose en el panel de alertas. Idempotente: en arranques posteriores borra 0 filas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PositionLimitsCleanupRunner implements CommandLineRunner {

  private static final List<String> OBSOLETE_ALERT_TYPES =
      List.of("STOP_LOSS", "TAKE_PROFIT", "TRAILING_STOP", "DCA_TARGET");

  private final PositionAlertRepository positionAlertRepository;

  @Override
  @Transactional
  public void run(String... args) {
    long deleted = positionAlertRepository.deleteByAlertTypeIn(OBSOLETE_ALERT_TYPES);
    if (deleted > 0) {
      log.info("PositionLimitsCleanup: eliminadas {} alertas de límites obsoletas", deleted);
    }
  }
}
