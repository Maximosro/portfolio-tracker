package com.sro.myportfoliotracker.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonteCarloRequest {

    /** Número de simulaciones a ejecutar */
    @Builder.Default
    private Integer numSimulations = 1000;

    /** Años a simular */
    @Builder.Default
    private Integer years = 20;

    /** Rentabilidad media anual esperada (%). Si null, usa XIRR real */
    private Double expectedReturnPct;

    /** Volatilidad anual (desviación estándar %). Si null, usa 15% por defecto */
    @Builder.Default
    private Double volatilityPct = 15.0;

    /** Aportación mensual (€). Si null, usa el plan de inversión */
    private Double monthlyContribution;

    /** Inflación anual (%) */
    @Builder.Default
    private Double inflationPct = 2.5;

    /** Objetivo de patrimonio (€). Para calcular probabilidad de alcanzarlo */
    private Double goalAmount;
}

