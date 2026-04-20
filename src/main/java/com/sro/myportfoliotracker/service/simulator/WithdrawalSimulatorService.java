package com.sro.myportfoliotracker.service.simulator;

import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.dto.simulator.WithdrawalRequest;
import com.sro.myportfoliotracker.dto.simulator.WithdrawalResult;
import com.sro.myportfoliotracker.dto.simulator.WithdrawalResult.RateComparison;
import com.sro.myportfoliotracker.dto.simulator.WithdrawalResult.YearWithdrawal;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.service.PortfolioMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalSimulatorService {

    private final PositionRepository positionRepository;
    private final PortfolioMetricsService metricsService;

    public WithdrawalResult simulate(WithdrawalRequest req) {
        // Portfolio actual
        double currentValue = positionRepository.findAll().stream()
                .filter(p -> p.getCurrentPrice() != null && p.getCurrentPrice() > 0)
                .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
                .sum();

        // Rentabilidad
        double expectedReturn;
        String returnSource;
        if (req.getExpectedReturnPct() != null) {
            expectedReturn = req.getExpectedReturnPct();
            returnSource = "custom";
        } else {
            try {
                PortfolioMetricsDto metrics = metricsService.calculateMetrics();
                expectedReturn = metrics.portfolioXirr() != null ? metrics.portfolioXirr() * 100.0 : 7.0;
            } catch (Exception e) {
                expectedReturn = 7.0;
            }
            returnSource = "xirr";
        }

        double inflationPct = req.getInflationPct() != null ? req.getInflationPct() : 2.5;
        int maxYears = req.getMaxYears() != null ? req.getMaxYears() : 40;
        double withdrawalRate = req.getWithdrawalRatePct() != null ? req.getWithdrawalRatePct() : 4.0;

        // Calcular retirada mensual inicial
        double initialMonthly;
        if (req.getFixedMonthlyWithdrawal() != null && req.getFixedMonthlyWithdrawal() > 0) {
            initialMonthly = req.getFixedMonthlyWithdrawal();
            withdrawalRate = (initialMonthly * 12.0 / currentValue) * 100.0;
        } else {
            initialMonthly = currentValue * withdrawalRate / 100.0 / 12.0;
        }

        // Simulación principal
        List<YearWithdrawal> evolution = simulateWithdrawal(currentValue, initialMonthly, expectedReturn, inflationPct, maxYears);

        // Resultados
        boolean survives = evolution.stream().noneMatch(YearWithdrawal::isDepleted);
        Integer depletionYear = evolution.stream().filter(YearWithdrawal::isDepleted).findFirst().map(YearWithdrawal::getYear).orElse(null);
        double finalValue = evolution.isEmpty() ? 0 : evolution.get(evolution.size() - 1).getPortfolioValueEnd();
        double totalWithdrawn = evolution.stream().mapToDouble(YearWithdrawal::getAnnualWithdrawal).sum();
        double lastYearWithdrawal = evolution.isEmpty() ? 0 : evolution.get(evolution.size() - 1).getAnnualWithdrawal();

        // Comparativa de tasas
        List<RateComparison> comparison = new ArrayList<>();
        if (Boolean.TRUE.equals(req.getShowComparison())) {
            double[] rates = {2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0, 6.0, 7.0, 8.0};
            for (double rate : rates) {
                double monthly = currentValue * rate / 100.0 / 12.0;
                List<YearWithdrawal> sim = simulateWithdrawal(currentValue, monthly, expectedReturn, inflationPct, maxYears);
                boolean s = sim.stream().noneMatch(YearWithdrawal::isDepleted);
                Integer dy = sim.stream().filter(YearWithdrawal::isDepleted).findFirst().map(YearWithdrawal::getYear).orElse(null);
                double fv = sim.isEmpty() ? 0 : sim.get(sim.size() - 1).getPortfolioValueEnd();

                comparison.add(RateComparison.builder()
                        .withdrawalRatePct(rate)
                        .monthlyWithdrawal(round2(monthly))
                        .annualWithdrawal(round2(monthly * 12))
                        .survives(s)
                        .depletionYear(dy)
                        .finalValue(round2(Math.max(fv, 0)))
                        .build());
            }
        }

        return WithdrawalResult.builder()
                .currentPortfolioValue(round2(currentValue))
                .withdrawalRatePct(round2(withdrawalRate))
                .initialMonthlyWithdrawal(round2(initialMonthly))
                .expectedReturnPct(round2(expectedReturn))
                .expectedReturnSource(returnSource)
                .inflationPct(inflationPct)
                .maxYears(maxYears)
                .portfolioSurvives(survives)
                .depletionYear(depletionYear)
                .finalPortfolioValue(round2(Math.max(finalValue, 0)))
                .totalWithdrawn(round2(totalWithdrawn))
                .lastYearWithdrawal(round2(lastYearWithdrawal))
                .yearlyEvolution(evolution)
                .rateComparison(comparison)
                .build();
    }

    private List<YearWithdrawal> simulateWithdrawal(double startValue, double initialMonthly,
                                                     double annualReturnPct, double inflationPct, int maxYears) {
        List<YearWithdrawal> result = new ArrayList<>();
        double portfolio = startValue;
        double monthlyWithdrawal = initialMonthly;
        double cumulativeWithdrawn = 0;
        double monthlyReturn = annualReturnPct / 100.0 / 12.0;

        for (int y = 1; y <= maxYears; y++) {
            double yearStart = portfolio;
            double yearWithdrawal = 0;
            double yearReturn = 0;
            boolean depleted = false;

            for (int m = 0; m < 12; m++) {
                // Primero retiras
                double withdrawal = Math.min(monthlyWithdrawal, portfolio);
                portfolio -= withdrawal;
                yearWithdrawal += withdrawal;

                if (portfolio <= 0) {
                    depleted = true;
                    break;
                }

                // Luego la cartera genera retorno
                double ret = portfolio * monthlyReturn;
                portfolio += ret;
                yearReturn += ret;
            }

            cumulativeWithdrawn += yearWithdrawal;

            result.add(YearWithdrawal.builder()
                    .year(y)
                    .portfolioValueStart(round2(yearStart))
                    .annualWithdrawal(round2(yearWithdrawal))
                    .annualReturn(round2(yearReturn))
                    .portfolioValueEnd(round2(Math.max(portfolio, 0)))
                    .cumulativeWithdrawn(round2(cumulativeWithdrawn))
                    .depleted(depleted)
                    .build());

            if (depleted) break;

            // Ajustar retirada por inflación para el siguiente año
            monthlyWithdrawal *= (1 + inflationPct / 100.0);
        }

        return result;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

