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
public class MonteCarloResult {

    private int numSimulations;
    private int years;
    private double expectedReturnPct;
    private String expectedReturnSource; // "xirr" or "custom"
    private double volatilityPct;
    private double monthlyContribution;
    private String monthlyContributionSource; // "plan" or "custom"
    private double inflationPct;
    private double currentPortfolioValue;

    // Final value percentiles
    private double p5;
    private double p10;
    private double p25;
    private double p50Median;
    private double p75;
    private double p90;
    private double p95;
    private double mean;

    // Real values (adjusted for inflation)
    private double p50Real;
    private double meanReal;

    // Goal probability
    private Double goalAmount;
    private Double goalProbabilityPct;

    // Probability of loss (final value < total contributed)
    private double probabilityOfLossPct;

    // Total contributed
    private double totalContributed;

    // Yearly percentile bands for fan chart
    private List<YearPercentiles> yearlyPercentiles;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class YearPercentiles {
        private int year;
        private double p5;
        private double p10;
        private double p25;
        private double p50;
        private double p75;
        private double p90;
        private double p95;
        private double mean;
        private double totalContributed;
    }
}

