package com.sro.myportfoliotracker.service.simulator;

import com.sro.myportfoliotracker.dto.simulator.CompoundInterestRequest;
import com.sro.myportfoliotracker.dto.simulator.CompoundInterestRequest.ContributionChange;
import com.sro.myportfoliotracker.dto.simulator.CompoundInterestRequest.ReturnChange;
import com.sro.myportfoliotracker.dto.simulator.CompoundInterestResult;
import com.sro.myportfoliotracker.dto.simulator.CompoundInterestResult.YearCompound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class CompoundInterestService {

    public CompoundInterestResult simulate(CompoundInterestRequest req) {
        double initialCapital = req.getInitialCapital() != null ? req.getInitialCapital() : 0;
        double baseMonthly = req.getMonthlyContribution() != null ? req.getMonthlyContribution() : 500;
        double baseReturn = req.getAnnualReturnPct() != null ? req.getAnnualReturnPct() : 7;
        double inflationPct = req.getInflationPct() != null ? req.getInflationPct() : 2.5;
        double feePct = req.getAnnualFeePct() != null ? req.getAnnualFeePct() : 0;
        int years = req.getYears() != null ? req.getYears() : 20;

        // Sort contribution/return changes
        List<ContributionChange> contChanges = req.getContributionChanges() != null
                ? req.getContributionChanges().stream().sorted(Comparator.comparingInt(ContributionChange::getFromYear)).toList()
                : List.of();
        List<ReturnChange> retChanges = req.getReturnChanges() != null
                ? req.getReturnChanges().stream().sorted(Comparator.comparingInt(ReturnChange::getFromYear)).toList()
                : List.of();

        // Simulate with fees
        List<YearCompound> evolution = new ArrayList<>();
        double portfolio = initialCapital;
        double totalContributed = initialCapital;
        double totalFees = 0;

        // Also simulate without fees for comparison
        double portfolioNoFees = initialCapital;

        for (int y = 1; y <= years; y++) {
            // Determine current monthly contribution
            double currentMonthly = baseMonthly;
            for (ContributionChange cc : contChanges) {
                if (y >= cc.getFromYear()) currentMonthly = cc.getAmount();
            }

            // Determine current return
            double currentReturn = baseReturn;
            for (ReturnChange rc : retChanges) {
                if (y >= rc.getFromYear()) currentReturn = rc.getReturnPct();
            }

            double monthlyRate = currentReturn / 100.0 / 12.0;
            double monthlyFeeRate = feePct / 100.0 / 12.0;
            double yearStart = portfolio;
            double yearContribution = 0;
            double yearReturn = 0;
            double yearFees = 0;

            for (int m = 0; m < 12; m++) {
                // Contribution
                portfolio += currentMonthly;
                portfolioNoFees += currentMonthly;
                yearContribution += currentMonthly;

                // Return
                double ret = portfolio * monthlyRate;
                portfolio += ret;
                yearReturn += ret;

                double retNoFees = portfolioNoFees * monthlyRate;
                portfolioNoFees += retNoFees;

                // Fee (deducted from portfolio)
                double fee = portfolio * monthlyFeeRate;
                portfolio -= fee;
                yearFees += fee;
            }

            totalContributed += yearContribution;
            totalFees += yearFees;

            double inflationFactor = Math.pow(1 + inflationPct / 100.0, y);
            double totalGains = portfolio - totalContributed;

            evolution.add(YearCompound.builder()
                    .year(y)
                    .portfolioValue(round2(portfolio))
                    .totalContributed(round2(totalContributed))
                    .yearlyContribution(round2(yearContribution))
                    .yearlyReturn(round2(yearReturn))
                    .yearlyFees(round2(yearFees))
                    .totalGains(round2(totalGains))
                    .realValue(round2(portfolio / inflationFactor))
                    .monthlyContribution(round2(currentMonthly))
                    .returnPctUsed(round2(currentReturn))
                    .build());
        }

        double totalGains = portfolio - totalContributed;
        double totalGainsAfterFees = totalGains; // fees already deducted from portfolio
        double inflationFactor = Math.pow(1 + inflationPct / 100.0, years);

        // Effective return (CAGR)
        double effectiveReturn = 0;
        if (totalContributed > 0 && portfolio > 0) {
            effectiveReturn = (Math.pow(portfolio / initialCapital, 1.0 / years) - 1) * 100;
            // Better approximation considering contributions
            if (initialCapital > 0) {
                effectiveReturn = (Math.pow(portfolio / initialCapital, 1.0 / years) - 1) * 100;
            }
        }

        return CompoundInterestResult.builder()
                .initialCapital(initialCapital)
                .baseMonthlyContribution(baseMonthly)
                .baseAnnualReturnPct(baseReturn)
                .inflationPct(inflationPct)
                .annualFeePct(feePct)
                .years(years)
                .finalValue(round2(portfolio))
                .finalRealValue(round2(portfolio / inflationFactor))
                .totalContributed(round2(totalContributed))
                .totalFees(round2(totalFees))
                .totalGains(round2(totalGains + totalFees)) // gross gains before fees
                .totalGainsAfterFees(round2(totalGainsAfterFees))
                .effectiveReturnPct(round2(baseReturn - feePct))
                .multiplier(round2(totalContributed > 0 ? portfolio / totalContributed : 0))
                .valueWithoutFees(round2(portfolioNoFees))
                .feesImpact(round2(portfolioNoFees - portfolio))
                .yearlyEvolution(evolution)
                .build();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

