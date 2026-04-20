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
public class MortgageResult {

    // ─── Inputs utilizados ───
    private double outstandingPrincipal;
    private double annualInterestRate;
    private int remainingYears;
    private double monthlyPayment;
    private double extraMonthly;
    private double investmentReturnPct;
    private String investmentReturnSource; // "xirr" | "custom"
    private double currentPortfolioValue;
    private double taxRatePct;
    private boolean mixedMortgage;
    private List<MortgageRequest.RatePeriod> ratePeriods;

    // ─── Resultado principal: ¿cuándo puede la cartera pagar la hipoteca? ───
    private Integer payoffMonth;           // Mes en que cartera >= deuda pendiente (null si nunca)
    private Double payoffPortfolioValue;   // Valor de cartera en ese momento
    private Double payoffDebtRemaining;    // Deuda restante en ese momento

    // ─── Comparativa: amortizar vs invertir ───
    private StrategyResult strategyAmortize;
    private StrategyResult strategyInvest;
    private String winnerStrategy;         // "AMORTIZAR" | "INVERTIR" | "EMPATE"
    private double advantageEur;           // Diferencia en patrimonio neto final

    // ─── Evolución mensual (muestreada por año para no enviar 360 puntos) ───
    private List<YearSnapshot> yearlyEvolution;

    // ─── Sensibilidad ───
    private List<SensitivityRow> sensitivity;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StrategyResult {
        private String name;
        private double totalInterestPaid;       // Intereses totales pagados a banco
        private double totalInvested;           // Total aportado a inversión
        private double finalPortfolioValue;     // Valor cartera al final
        private double finalPortfolioAfterTax;  // Después de impuestos sobre ganancias
        private double netWealthEnd;            // Patrimonio neto = cartera - deuda restante
        private int monthsToPayOff;             // Meses hasta cancelar hipoteca
        private double totalPaidToBank;         // Total pagado al banco (cuotas + extras)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class YearSnapshot {
        private int year;
        private double debtRemaining;
        private double portfolioValue;
        private double netWealth;                // portfolio - debt
        private double cumulativeInterestPaid;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SensitivityRow {
        private double investmentReturn;
        private double mortgageRate;
        private String winner;                   // "AMORTIZAR" | "INVERTIR"
        private double advantageEur;
    }
}

