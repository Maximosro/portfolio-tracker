package com.sro.myportfoliotracker.dto.simulator;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MortgageRequest {

  /**
   * Capital pendiente de la hipoteca (€)
   */
  private Double outstandingPrincipal;

  /**
   * Tipo de interés anual (%) — ej: 2.5. Usado si ratePeriods es null (tipo fijo)
   */
  private Double annualInterestRate;

  /**
   * Años restantes de hipoteca
   */
  private Integer remainingYears;

  /**
   * Cuota mensual actual (€). Si es null, se calcula del principal/tipo/plazo
   */
  private Double monthlyPayment;

  /**
   * Cantidad extra mensual disponible para amortizar O invertir (€)
   */
  @Builder.Default
  private Double extraMonthly = 0.0;

  /**
   * Rentabilidad anual esperada de la inversión (%). Si null, usa XIRR real
   */
  private Double investmentReturnPct;

  /**
   * Tipo marginal IRPF para plusvalías al liquidar inversión (%) — por defecto 21
   */
  @Builder.Default
  private Double taxRatePct = 21.0;

  /**
   * Periodos de tipo de interés para hipotecas mixtas. Si es null o vacío, se usa
   * annualInterestRate como tipo fijo para toda la vida. Ejemplo: [{years:5, rate:1.5}, {years:20,
   * rate:3.0}] = 5 años al 1.5%, resto al 3%
   */
  private List<RatePeriod> ratePeriods;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RatePeriod {

    /**
     * Número de años de este tramo
     */
    private int years;
    /**
     * Tipo de interés anual (%) para este tramo
     */
    private double rate;
  }
}
