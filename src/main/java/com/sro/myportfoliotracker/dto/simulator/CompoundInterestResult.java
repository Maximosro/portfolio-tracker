package com.sro.myportfoliotracker.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompoundInterestResult {

    // Parámetros
    private double initialCapital;
    private double baseMonthlyContribution;
    private double baseAnnualReturnPct;
    private double inflationPct;
    private double annualFeePct;
    private int years;

    // Resultado final
    private double finalValue;
    private double finalRealValue;           // Ajustado inflación
    private double totalContributed;
    private double totalFees;                // Comisiones totales pagadas
    private double totalGains;               // Ganancias brutas
    private double totalGainsAfterFees;      // Ganancias netas de comisiones
    private double effectiveReturnPct;       // Rentabilidad efectiva real (descontando comisiones)
    private double multiplier;               // finalValue / totalContributed

    // Impacto de comisiones
    private double valueWithoutFees;         // Lo que tendrías sin comisiones
    private double feesImpact;               // Diferencia

    // Evolución anual
    private List<YearCompound> yearlyEvolution;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class YearCompound {
        private int year;
        private double portfolioValue;
        private double totalContributed;
        private double yearlyContribution;
        private double yearlyReturn;          // Ganancia bruta del año
        private double yearlyFees;            // Comisiones del año
        private double totalGains;
        private double realValue;             // Ajustado inflación
        private double monthlyContribution;   // Aportación vigente ese año
        private double returnPctUsed;         // Rentabilidad vigente ese año
    }
}

