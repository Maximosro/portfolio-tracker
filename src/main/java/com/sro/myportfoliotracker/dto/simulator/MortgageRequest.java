package com.sro.myportfoliotracker.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MortgageRequest {

    /** Capital pendiente de la hipoteca (€) */
    private Double outstandingPrincipal;

    /** Tipo de interés anual (%) — ej: 2.5 */
    private Double annualInterestRate;

    /** Años restantes de hipoteca */
    private Integer remainingYears;

    /** Cuota mensual actual (€). Si es null, se calcula del principal/tipo/plazo */
    private Double monthlyPayment;

    /** Cantidad extra mensual disponible para amortizar O invertir (€) */
    @Builder.Default
    private Double extraMonthly = 0.0;

    /** Rentabilidad anual esperada de la inversión (%). Si null, usa XIRR real */
    private Double investmentReturnPct;

    /** Tipo marginal IRPF para plusvalías al liquidar inversión (%) — por defecto 21 */
    @Builder.Default
    private Double taxRatePct = 21.0;
}

