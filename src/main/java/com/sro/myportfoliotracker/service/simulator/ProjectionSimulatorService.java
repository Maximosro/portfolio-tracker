package com.sro.myportfoliotracker.service.simulator;

import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.dto.simulator.ProjectionRequest;
import com.sro.myportfoliotracker.dto.simulator.ProjectionResult;
import com.sro.myportfoliotracker.dto.simulator.ProjectionResult.Summary;
import com.sro.myportfoliotracker.dto.simulator.ProjectionResult.YearProjection;
import com.sro.myportfoliotracker.model.InvestmentPlan;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.InvestmentPlanRepository;
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
public class ProjectionSimulatorService {

    private final PositionRepository positionRepository;
    private final InvestmentPlanRepository investmentPlanRepository;
    private final PortfolioMetricsService metricsService;

    public ProjectionResult simulate(ProjectionRequest request) {
        List<Position> positions = positionRepository.findAll();

        // Valor actual de la cartera
        double currentValue = positions.stream()
                .filter(p -> p.getCurrentPrice() != null && p.getCurrentPrice() > 0)
                .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
                .sum();

        double currentInvested = positions.stream()
                .mapToDouble(p -> p.getShares() * p.getAvgPrice())
                .sum();

        // Aportación mensual: del request o del plan de inversión
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

        // Rentabilidad esperada: del request o del XIRR real
        double expectedReturn;
        String returnSource;
        if (request.getExpectedReturnPct() != null) {
            expectedReturn = request.getExpectedReturnPct();
            returnSource = "custom";
        } else {
            try {
                PortfolioMetricsDto metrics = metricsService.calculateMetrics();
                expectedReturn = metrics.portfolioXirr() != null
                        ? metrics.portfolioXirr() * 100.0 // XIRR viene como decimal
                        : 7.0; // Default si no hay XIRR
            } catch (Exception e) {
                log.warn("No se pudo calcular XIRR, usando 7% por defecto", e);
                expectedReturn = 7.0;
            }
            returnSource = "xirr";
        }

        double inflationPct = request.getInflationPct() != null ? request.getInflationPct() : 2.5;
        int years = request.getYears() != null ? request.getYears() : 20;

        // Calcular proyecciones
        List<YearProjection> baseProjection = project(currentValue, currentInvested, monthlyContribution, expectedReturn, inflationPct, years);
        List<YearProjection> pessimistic = null;
        List<YearProjection> optimistic = null;

        if (Boolean.TRUE.equals(request.getShowScenarios())) {
            pessimistic = project(currentValue, currentInvested, monthlyContribution, Math.max(expectedReturn - 3, 0), inflationPct, years);
            optimistic = project(currentValue, currentInvested, monthlyContribution, expectedReturn + 3, inflationPct, years);
        }

        // Resumen
        YearProjection lastBase = baseProjection.get(baseProjection.size() - 1);
        Summary summary = Summary.builder()
                .finalValueBase(lastBase.getPortfolioValue())
                .finalValuePessimistic(pessimistic != null ? pessimistic.get(pessimistic.size() - 1).getPortfolioValue() : 0)
                .finalValueOptimistic(optimistic != null ? optimistic.get(optimistic.size() - 1).getPortfolioValue() : 0)
                .finalRealValueBase(lastBase.getRealValue())
                .totalContributed(lastBase.getTotalContributed())
                .totalGainsBase(lastBase.getTotalGains())
                .monthlyPassiveIncome4Pct(round2(lastBase.getPortfolioValue() * 0.04 / 12))
                .build();

        return ProjectionResult.builder()
                .currentPortfolioValue(round2(currentValue))
                .currentInvested(round2(currentInvested))
                .monthlyContribution(monthlyContribution)
                .monthlyContributionSource(monthlySource)
                .expectedReturnPct(round2(expectedReturn))
                .expectedReturnSource(returnSource)
                .inflationPct(inflationPct)
                .years(years)
                .baseProjection(baseProjection)
                .pessimisticProjection(pessimistic)
                .optimisticProjection(optimistic)
                .summary(summary)
                .build();
    }

    private List<YearProjection> project(double startValue, double startInvested, double monthlyContribution, double annualReturnPct, double inflationPct, int years) {
        List<YearProjection> result = new ArrayList<>();
        double monthlyRate = annualReturnPct / 100.0 / 12.0;
        double value = startValue;
        double totalContributed = startInvested;

        for (int y = 1; y <= years; y++) {
            double yearStart = value;
            double yearContribution = 0;

            for (int m = 0; m < 12; m++) {
                value += monthlyContribution;
                yearContribution += monthlyContribution;
                value *= (1 + monthlyRate);
            }

            totalContributed += yearContribution;
            double totalGains = value - totalContributed;
            double inflationFactor = Math.pow(1 + inflationPct / 100.0, y);
            double realValue = value / inflationFactor;

            result.add(YearProjection.builder()
                    .year(y)
                    .portfolioValue(round2(value))
                    .totalContributed(round2(totalContributed))
                    .totalGains(round2(totalGains))
                    .realValue(round2(realValue))
                    .yearlyContribution(round2(yearContribution))
                    .yearlyGain(round2(value - yearStart - yearContribution))
                    .build());
        }

        return result;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

