package com.sro.myportfoliotracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PositionDetail;
import com.sro.myportfoliotracker.repository.PositionAlertRepository;
import com.sro.myportfoliotracker.repository.PositionDetailRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tras la eliminación de los límites de posición, el motor de alertas solo debe generar
 * alertas de precio (ALERT_ABOVE / ALERT_BELOW). Los campos de límites (stopLoss,
 * takeProfit, trailingStopPct, dcaTargetPrice) ya no existen en la entidad, por lo que
 * es imposible que se generen alertas de esos tipos.
 */
@SpringBootTest
class AlertServiceTest {

  private static final Set<String> ALLOWED_TYPES = Set.of("ALERT_ABOVE", "ALERT_BELOW");

  @Autowired
  private AlertService alertService;

  @Autowired
  private PositionRepository positionRepository;

  @Autowired
  private PositionDetailRepository positionDetailRepository;

  @Autowired
  private PositionAlertRepository positionAlertRepository;

  @BeforeEach
  void cleanDb() {
    positionAlertRepository.deleteAll();
    positionDetailRepository.deleteAll();
    positionRepository.deleteAll();
  }

  @Test
  void getTodayAlerts_onlyReturnsPriceAlerts_whenThresholdsCrossed() {
    // Precio 90: cruza la alerta superior (80) y la inferior (100) → deben dispararse
    // ALERT_ABOVE y ALERT_BELOW, y solo esos tipos.
    positionRepository.save(Position.builder()
        .ticker("LMTST")
        .name("Limits Test")
        .shares(10.0)
        .avgPrice(100.0)
        .currentPrice(90.0)
        .build());
    positionDetailRepository.save(PositionDetail.builder()
        .ticker("LMTST")
        .alertPriceAbove(80.0)
        .alertPriceBelow(100.0)
        .build());

    List<AlertDto> alerts = alertService.getTodayAlerts();

    Set<String> types = alerts.stream().map(AlertDto::getType).collect(Collectors.toSet());
    assertTrue(ALLOWED_TYPES.containsAll(types),
        "Solo deben generarse alertas de precio, pero se obtuvo: " + types);
    assertEquals(Set.of("ALERT_ABOVE", "ALERT_BELOW"), types,
        "Deben dispararse las dos alertas de precio configuradas");
  }

  @Test
  void getTodayAlerts_empty_whenPriceWithinAlertThresholds() {
    positionRepository.save(Position.builder()
        .ticker("NOLMT")
        .name("No Limits")
        .shares(10.0)
        .avgPrice(100.0)
        .currentPrice(100.0)
        .build());
    positionDetailRepository.save(PositionDetail.builder()
        .ticker("NOLMT")
        .alertPriceAbove(200.0)
        .alertPriceBelow(50.0)
        .build());

    List<AlertDto> alerts = alertService.getTodayAlerts();

    assertTrue(alerts.isEmpty(), "Sin cruce de umbrales no debe haber alertas: " + alerts);
  }
}
