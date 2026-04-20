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
public class ProjectionResult {

    /** Parámetros utilizados */
    private double currentPortfolioValue;
    private double currentInvested;
    private double monthlyContribution;
    private String monthlyContributionSource; // "plan" | "custom"
    private double expectedReturnPct;
    private String expectedReturnSource; // "xirr" | "custom"
    private double inflationPct;
    private int years;

    /** Proyección principal (base) */
    private List<YearProjection> baseProjection;

    /** Escenario pesimista (return - 3pp) */
    private List<YearProjection> pessimisticProjection;

    /** Escenario optimista (return + 3pp) */
    private List<YearProjection> optimisticProjection;

    /** Resumen final */
    private Summary summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class YearProjection {
        private int year;
        private double portfolioValue;       // Valor nominal
        private double totalContributed;     // Total aportado acumulado
        private double totalGains;           // Ganancias acumuladas
        private double realValue;            // Valor ajustado por inflación
        private double yearlyContribution;   // Aportación del año
        private double yearlyGain;           // Ganancia del año
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private double finalValueBase;
        private double finalValuePessimistic;
        private double finalValueOptimistic;
        private double finalRealValueBase;       // Ajustado por inflación
        private double totalContributed;
        private double totalGainsBase;
        private double monthlyPassiveIncome4Pct; // Regla del 4%
    }
}

