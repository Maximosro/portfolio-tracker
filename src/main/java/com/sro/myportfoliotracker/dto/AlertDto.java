package com.sro.myportfoliotracker.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDto {

  private String ticker;
  private String name;
  private String color;
  private String type;        // STOP_LOSS, TAKE_PROFIT, TRAILING_STOP, DCA_TARGET, ALERT_ABOVE, ALERT_BELOW
  private String severity;    // DANGER, WARNING, INFO
  private String message;
  private Double currentPrice;
  private Double limitPrice;
  private Double distancePct; // % de distancia al límite (negativo = ya traspasado)
  private Long alertId;       // ID del PositionAlert persistido (null si no persistido)
  private Instant triggeredAt; // Momento del disparo (UTC)
}

