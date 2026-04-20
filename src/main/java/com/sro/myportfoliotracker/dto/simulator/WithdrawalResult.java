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
public class WithdrawalResult {

    // ─── Parámetros utilizados ───
    private double currentPortfolioValue;
    private double withdrawalRatePct;
    private double initialMonthlyWithdrawal;
    private double expectedReturnPct;
    private String expectedReturnSource;
    private double inflationPct;
    private int maxYears;

    // ─── Resultado principal ───
    private boolean portfolioSurvives;        // ¿Sobrevive los maxYears?
    private Integer depletionYear;            // Año en que se agota (null si sobrevive)
    private double finalPortfolioValue;       // Valor al final (0 si se agota)
    private double totalWithdrawn;            // Total retirado durante la simulación
    private double lastYearWithdrawal;        // Retirada anual del último año (ajustada inflación)

    // ─── Evolución anual ───
    private List<YearWithdrawal> yearlyEvolution;

    // ─── Comparativa de tasas ───
    private List<RateComparison> rateComparison;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class YearWithdrawal {
        private int year;
        private double portfolioValueStart;
        private double annualWithdrawal;        // Lo que retiras ese año (ajustado por inflación)
        private double annualReturn;            // Lo que gana la cartera ese año
        private double portfolioValueEnd;       // Valor al final del año
        private double cumulativeWithdrawn;     // Total acumulado retirado
        private boolean depleted;               // Si se agotó este año
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RateComparison {
        private double withdrawalRatePct;
        private double monthlyWithdrawal;       // €/mes inicial
        private double annualWithdrawal;        // €/año inicial
        private boolean survives;               // ¿Sobrevive maxYears?
        private Integer depletionYear;          // Año de agotamiento
        private double finalValue;              // Valor final si sobrevive
    }
}

