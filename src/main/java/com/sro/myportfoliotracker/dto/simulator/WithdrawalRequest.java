package com.sro.myportfoliotracker.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequest {

  /**
   * Tasa de retirada anual en % (ej: 4.0 = regla del 4%)
   */
  @Builder.Default
  private Double withdrawalRatePct = 4.0;

  /**
   * O importe fijo mensual a retirar (€). Si se indica, ignora withdrawalRatePct
   */
  private Double fixedMonthlyWithdrawal;

  /**
   * Rentabilidad anual esperada (%). Null = usa XIRR real
   */
  private Double expectedReturnPct;

  /**
   * Inflación anual (%) para ajustar retiradas cada año
   */
  @Builder.Default
  private Double inflationPct = 2.5;

  /**
   * Años máximos a simular
   */
  @Builder.Default
  private Integer maxYears = 40;

  /**
   * Si true, muestra múltiples tasas de retirada para comparar
   */
  @Builder.Default
  private Boolean showComparison = true;
}

