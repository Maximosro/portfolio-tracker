package com.sro.myportfoliotracker.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectionRequest {

    /** Años a proyectar (por defecto 20) */
    @Builder.Default
    private Integer years = 20;

    /** Aportación mensual en €. Si es null, usa la del InvestmentPlan */
    private Double monthlyContribution;

    /** Rentabilidad anual esperada en % (ej: 7.0). Si es null, usa el XIRR real */
    private Double expectedReturnPct;

    /** Inflación anual en % para mostrar valor real (por defecto 2.5) */
    @Builder.Default
    private Double inflationPct = 2.5;

    /** Si true, muestra también la proyección pesimista y optimista */
    @Builder.Default
    private Boolean showScenarios = true;
}

