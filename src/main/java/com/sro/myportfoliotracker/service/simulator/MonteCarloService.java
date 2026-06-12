package com.sro.myportfoliotracker.service.simulator;

import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.dto.simulator.MonteCarloRequest;
import com.sro.myportfoliotracker.dto.simulator.MonteCarloResult;
import com.sro.myportfoliotracker.dto.simulator.MonteCarloResult.YearPercentiles;
import com.sro.myportfoliotracker.model.InvestmentPlan;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.InvestmentPlanRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.service.PortfolioMetricsService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonteCarloService {

  private final PositionRepository positionRepository;
  private final InvestmentPlanRepository investmentPlanRepository;
  private final PortfolioMetricsService metricsService;

  public MonteCarloResult simulate(MonteCarloRequest request) {
    List<Position> positions = positionRepository.findAll();

    // Current portfolio value
    double currentValue = positions.stream()
        .filter(p -> p.getCurrentPrice() != null && p.getCurrentPrice() > 0)
        .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
        .sum();

    // Monthly contribution
    double monthlyContribution;
    String monthlySource;
    if (request.getMonthlyContribution() != null) {
      monthlyContribution = request.getMonthlyContribution();
      monthlySource = "custom";
    } else {
      monthlyContribution = investmentPlanRepository.findById(1L)
          .map(InvestmentPlan::getMonthlyBudget)
          .orElse(0.0);
      monthlySource = "plan";
    }

    // Expected return
    double expectedReturn;
    String returnSource;
    if (request.getExpectedReturnPct() != null) {
      expectedReturn = request.getExpectedReturnPct();
      returnSource = "custom";
    } else {
      try {
        PortfolioMetricsDto metrics = metricsService.calculateMetrics();
        expectedReturn = metrics.portfolioXirr() != null
            ? metrics.portfolioXirr() * 100.0
            : 7.0;
      } catch (Exception e) {
        log.warn("No se pudo calcular XIRR, usando 7% por defecto", e);
        expectedReturn = 7.0;
      }
      returnSource = "xirr";
    }

    double volatility = request.getVolatilityPct() != null ? request.getVolatilityPct() : 15.0;
    double inflationPct = request.getInflationPct() != null ? request.getInflationPct() : 2.5;
    int years = request.getYears() != null ? request.getYears() : 20;
    int numSims =
        request.getNumSimulations() != null ? Math.min(request.getNumSimulations(), 10000) : 1000;

    // Run simulations
    // finalValues[sim] = final portfolio value for simulation sim
    double[] finalValues = new double[numSims];
    // yearValues[year][sim] = portfolio value at end of year for each sim
    double[][] yearValues = new double[years][numSims];

    double annualMean = expectedReturn / 100.0;
    double annualStd = volatility / 100.0;

    // Use log-normal model: ln(1+r) ~ N(mu, sigma^2)
    // Convert arithmetic mean/std to log-normal parameters
    double logMu = Math.log(1 + annualMean) - 0.5 * Math.pow(annualStd / (1 + annualMean), 2);
    double logSigma = annualStd / (1 + annualMean);

    Random rng = ThreadLocalRandom.current();

    for (int sim = 0; sim < numSims; sim++) {
      double portfolio = currentValue;

      for (int y = 0; y < years; y++) {
        // Generate annual return using log-normal
        double z = rng.nextGaussian();
        double annualReturn = Math.exp(logMu + logSigma * z) - 1;

        // Apply monthly: distribute annual return across 12 months
        double monthlyReturn = Math.pow(1 + annualReturn, 1.0 / 12.0) - 1;

        for (int m = 0; m < 12; m++) {
          portfolio += monthlyContribution;
          portfolio *= (1 + monthlyReturn);
        }

        yearValues[y][sim] = portfolio;
      }

      finalValues[sim] = portfolio;
    }

    // Sort final values for percentiles
    Arrays.sort(finalValues);

    double totalContributed = currentValue + monthlyContribution * 12 * years;

    // Calculate percentiles
    double p5 = percentile(finalValues, 5);
    double p10 = percentile(finalValues, 10);
    double p25 = percentile(finalValues, 25);
    double p50 = percentile(finalValues, 50);
    double p75 = percentile(finalValues, 75);
    double p90 = percentile(finalValues, 90);
    double p95 = percentile(finalValues, 95);
    double mean = Arrays.stream(finalValues).average().orElse(0);

    double inflationFactor = Math.pow(1 + inflationPct / 100.0, years);

    // Probability of loss
    long lossCount = Arrays.stream(finalValues).filter(v -> v < totalContributed).count();
    double probLoss = (double) lossCount / numSims * 100.0;

    // Goal probability
    Double goalProb = null;
    if (request.getGoalAmount() != null && request.getGoalAmount() > 0) {
      long goalCount = Arrays.stream(finalValues).filter(v -> v >= request.getGoalAmount()).count();
      goalProb = (double) goalCount / numSims * 100.0;
    }

    // Yearly percentiles for fan chart
    List<YearPercentiles> yearlyPercentiles = new ArrayList<>();
    double contributedSoFar = currentValue;
    for (int y = 0; y < years; y++) {
      contributedSoFar += monthlyContribution * 12;
      double[] yearSorted = Arrays.copyOf(yearValues[y], numSims);
      Arrays.sort(yearSorted);

      yearlyPercentiles.add(YearPercentiles.builder()
          .year(y + 1)
          .p5(round2(percentile(yearSorted, 5)))
          .p10(round2(percentile(yearSorted, 10)))
          .p25(round2(percentile(yearSorted, 25)))
          .p50(round2(percentile(yearSorted, 50)))
          .p75(round2(percentile(yearSorted, 75)))
          .p90(round2(percentile(yearSorted, 90)))
          .p95(round2(percentile(yearSorted, 95)))
          .mean(round2(Arrays.stream(yearSorted).average().orElse(0)))
          .totalContributed(round2(contributedSoFar))
          .build());
    }

    return MonteCarloResult.builder()
        .numSimulations(numSims)
        .years(years)
        .expectedReturnPct(round2(expectedReturn))
        .expectedReturnSource(returnSource)
        .volatilityPct(round2(volatility))
        .monthlyContribution(round2(monthlyContribution))
        .monthlyContributionSource(monthlySource)
        .inflationPct(round2(inflationPct))
        .currentPortfolioValue(round2(currentValue))
        .p5(round2(p5))
        .p10(round2(p10))
        .p25(round2(p25))
        .p50Median(round2(p50))
        .p75(round2(p75))
        .p90(round2(p90))
        .p95(round2(p95))
        .mean(round2(mean))
        .p50Real(round2(p50 / inflationFactor))
        .meanReal(round2(mean / inflationFactor))
        .goalAmount(request.getGoalAmount())
        .goalProbabilityPct(goalProb != null ? round2(goalProb) : null)
        .probabilityOfLossPct(round2(probLoss))
        .totalContributed(round2(totalContributed))
        .yearlyPercentiles(yearlyPercentiles)
        .build();
  }

  private double percentile(double[] sorted, int p) {
    double index = (p / 100.0) * (sorted.length - 1);
    int lower = (int) Math.floor(index);
    int upper = (int) Math.ceil(index);
    if (lower == upper) {
      return sorted[lower];
    }
    double frac = index - lower;
    return sorted[lower] * (1 - frac) + sorted[upper] * frac;
  }

  private double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}

