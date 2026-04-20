package com.sro.myportfoliotracker.service.simulator;

import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.dto.simulator.MortgageRequest;
import com.sro.myportfoliotracker.dto.simulator.MortgageRequest.RatePeriod;
import com.sro.myportfoliotracker.dto.simulator.MortgageResult;
import com.sro.myportfoliotracker.dto.simulator.MortgageResult.*;
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
public class MortgageSimulatorService {

    private final PositionRepository positionRepository;
    private final PortfolioMetricsService metricsService;

    public MortgageResult simulate(MortgageRequest req) {
        // Valor actual de cartera
        double currentPortfolio = positionRepository.findAll().stream()
                .filter(p -> p.getCurrentPrice() != null && p.getCurrentPrice() > 0)
                .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
                .sum();

        double currentInvested = positionRepository.findAll().stream()
                .mapToDouble(p -> p.getShares() * p.getAvgPrice())
                .sum();

        // Rentabilidad inversión
        double investReturn;
        String returnSource;
        if (req.getInvestmentReturnPct() != null) {
            investReturn = req.getInvestmentReturnPct();
            returnSource = "custom";
        } else {
            try {
                PortfolioMetricsDto metrics = metricsService.calculateMetrics();
                investReturn = metrics.portfolioXirr() != null ? metrics.portfolioXirr() * 100.0 : 7.0;
            } catch (Exception e) {
                investReturn = 7.0;
            }
            returnSource = "xirr";
        }

        double principal = req.getOutstandingPrincipal();
        double extra = req.getExtraMonthly() != null ? req.getExtraMonthly() : 0;
        double taxRate = req.getTaxRatePct() != null ? req.getTaxRatePct() : 21.0;
        boolean isMixed = req.getRatePeriods() != null && req.getRatePeriods().size() > 1;

        // For mixed mortgages, calculate remainingYears from sum of period years
        int remainingYears;
        if (isMixed) {
            remainingYears = req.getRatePeriods().stream().mapToInt(RatePeriod::getYears).sum();
        } else {
            remainingYears = req.getRemainingYears();
        }

        // Build monthly rate schedule for mixed mortgages
        double[] monthlyRates = buildMonthlyRateSchedule(req, remainingYears);
        // Weighted average rate for display
        double annualRate = calculateWeightedAverageRate(req, remainingYears);

        // Calcular cuota mensual (o array de cuotas si es mixta con recalculo por tramo)
        double monthlyPayment;
        if (req.getMonthlyPayment() != null && req.getMonthlyPayment() > 0) {
            monthlyPayment = req.getMonthlyPayment();
        } else {
            double initialRate = monthlyRates[0] * 12 * 100;
            monthlyPayment = calculateMonthlyPayment(principal, initialRate, remainingYears);
        }

        // Build monthly payment schedule: recalculates payment at each period transition
        double[] monthlyPayments = buildMonthlyPaymentSchedule(req, principal, monthlyPayment, remainingYears);

        // ─── 1. ¿Cuándo puede la cartera pagar la hipoteca? (escenario: invertir el extra) ───
        Integer payoffMonth = null;
        Double payoffPortfolio = null;
        Double payoffDebt = null;
        {
            double debt = principal;
            double portfolio = currentPortfolio;
            double investMonthlyRate = investReturn / 100.0 / 12.0;
            int maxMonths = remainingYears * 12;

            for (int m = 1; m <= maxMonths; m++) {
                double monthlyRate = monthlyRates[m - 1];
                double mp = monthlyPayments[m - 1];
                double interest = debt * monthlyRate;
                double principalPaid = mp - interest;
                if (principalPaid > debt) principalPaid = debt;
                debt -= principalPaid;
                if (debt < 0) debt = 0;

                portfolio += extra;
                portfolio *= (1 + investMonthlyRate);

                if (portfolio >= debt && payoffMonth == null && debt > 0) {
                    payoffMonth = m;
                    payoffPortfolio = round2(portfolio);
                    payoffDebt = round2(debt);
                }
                if (debt <= 0) break;
            }
        }

        // ─── 2. Estrategia AMORTIZAR ───
        StrategyResult amortizeResult = simulateAmortize(principal, monthlyRates, monthlyPayments, extra, remainingYears, currentPortfolio, currentInvested, investReturn, taxRate);

        // ─── 3. Estrategia INVERTIR ───
        StrategyResult investResult = simulateInvest(principal, monthlyRates, monthlyPayments, extra, remainingYears, currentPortfolio, currentInvested, investReturn, taxRate);

        // ─── Winner ───
        String winner;
        double advantage;
        if (Math.abs(investResult.getNetWealthEnd() - amortizeResult.getNetWealthEnd()) < 100) {
            winner = "EMPATE";
            advantage = 0;
        } else if (investResult.getNetWealthEnd() > amortizeResult.getNetWealthEnd()) {
            winner = "INVERTIR";
            advantage = round2(investResult.getNetWealthEnd() - amortizeResult.getNetWealthEnd());
        } else {
            winner = "AMORTIZAR";
            advantage = round2(amortizeResult.getNetWealthEnd() - investResult.getNetWealthEnd());
        }

        // ─── 4. Evolución anual ───
        List<YearSnapshot> evolution = buildEvolution(principal, monthlyRates, monthlyPayments, extra, remainingYears, currentPortfolio, investReturn);

        // ─── 5. Sensibilidad ───
        List<SensitivityRow> sensitivity = buildSensitivity(principal, annualRate, extra, remainingYears, currentPortfolio, currentInvested, taxRate);

        return MortgageResult.builder()
                .outstandingPrincipal(principal)
                .annualInterestRate(round2(annualRate))
                .remainingYears(remainingYears)
                .monthlyPayment(round2(monthlyPayment))
                .extraMonthly(extra)
                .investmentReturnPct(round2(investReturn))
                .investmentReturnSource(returnSource)
                .currentPortfolioValue(round2(currentPortfolio))
                .taxRatePct(taxRate)
                .mixedMortgage(isMixed)
                .ratePeriods(req.getRatePeriods())
                .payoffMonth(payoffMonth)
                .payoffPortfolioValue(payoffPortfolio)
                .payoffDebtRemaining(payoffDebt)
                .strategyAmortize(amortizeResult)
                .strategyInvest(investResult)
                .winnerStrategy(winner)
                .advantageEur(advantage)
                .yearlyEvolution(evolution)
                .sensitivity(sensitivity)
                .build();
    }

    /**
     * Builds an array of monthly interest rates (as decimal) for each month of the mortgage.
     * Supports mixed mortgages with multiple rate periods.
     */
    private double[] buildMonthlyRateSchedule(MortgageRequest req, int remainingYears) {
        int totalMonths = remainingYears * 12;
        double[] rates = new double[totalMonths];

        if (req.getRatePeriods() == null || req.getRatePeriods().isEmpty()) {
            // Fixed rate
            double monthlyRate = req.getAnnualInterestRate() / 100.0 / 12.0;
            for (int i = 0; i < totalMonths; i++) rates[i] = monthlyRate;
        } else {
            // Mixed: fill periods sequentially
            int idx = 0;
            for (RatePeriod period : req.getRatePeriods()) {
                double monthlyRate = period.getRate() / 100.0 / 12.0;
                int periodMonths = period.getYears() * 12;
                for (int m = 0; m < periodMonths && idx < totalMonths; m++, idx++) {
                    rates[idx] = monthlyRate;
                }
            }
            // If periods don't cover all months, extend last rate
            if (idx < totalMonths) {
                double lastRate = rates[idx - 1];
                for (; idx < totalMonths; idx++) rates[idx] = lastRate;
            }
        }
        return rates;
    }

    /**
     * Calculates weighted average annual rate for display purposes.
     */
    private double calculateWeightedAverageRate(MortgageRequest req, int remainingYears) {
        if (req.getRatePeriods() == null || req.getRatePeriods().isEmpty()) {
            return req.getAnnualInterestRate();
        }
        int totalYears = remainingYears;
        double weightedSum = 0;
        int coveredYears = 0;
        for (RatePeriod p : req.getRatePeriods()) {
            int yrs = Math.min(p.getYears(), totalYears - coveredYears);
            weightedSum += p.getRate() * yrs;
            coveredYears += yrs;
            if (coveredYears >= totalYears) break;
        }
        return coveredYears > 0 ? weightedSum / coveredYears : req.getAnnualInterestRate();
    }

    private StrategyResult simulateAmortize(double principal, double[] monthlyRates, double[] monthlyPayments,
                                            double extra, int years, double portfolioStart, double investedStart,
                                            double investReturn, double taxRate) {
        double debt = principal;
        double portfolio = portfolioStart;
        double totalInvested = investedStart;
        double investMonthlyRate = investReturn / 100.0 / 12.0;
        int maxMonths = years * 12;
        double totalInterest = 0;
        double totalPaid = 0;
        int monthsToPayOff = maxMonths;

        for (int m = 1; m <= maxMonths; m++) {
            double mp = monthlyPayments[m - 1];
            if (debt <= 0) {
                double freeAmount = mp + extra;
                portfolio += freeAmount;
                totalInvested += freeAmount;
                portfolio *= (1 + investMonthlyRate);
                continue;
            }

            double interest = debt * monthlyRates[m - 1];
            totalInterest += interest;

            double principalPaid = mp - interest + extra;
            if (principalPaid > debt) {
                totalPaid += debt + interest;
                debt = 0;
                monthsToPayOff = m;
            } else {
                debt -= principalPaid;
                totalPaid += mp + extra;
            }

            portfolio *= (1 + investMonthlyRate);
        }

        double gains = portfolio - totalInvested;
        double tax = gains > 0 ? gains * taxRate / 100.0 : 0;
        double portfolioAfterTax = portfolio - tax;

        return StrategyResult.builder()
                .name("Amortizar")
                .totalInterestPaid(round2(totalInterest))
                .totalInvested(round2(totalInvested))
                .finalPortfolioValue(round2(portfolio))
                .finalPortfolioAfterTax(round2(portfolioAfterTax))
                .netWealthEnd(round2(portfolioAfterTax - debt))
                .monthsToPayOff(monthsToPayOff)
                .totalPaidToBank(round2(totalPaid))
                .build();
    }

    private StrategyResult simulateInvest(double principal, double[] monthlyRates, double[] monthlyPayments,
                                          double extra, int years, double portfolioStart, double investedStart,
                                          double investReturn, double taxRate) {
        double debt = principal;
        double portfolio = portfolioStart;
        double totalInvested = investedStart;
        double investMonthlyRate = investReturn / 100.0 / 12.0;
        int maxMonths = years * 12;
        double totalInterest = 0;
        double totalPaid = 0;
        int monthsToPayOff = maxMonths;

        for (int m = 1; m <= maxMonths; m++) {
            double mp = monthlyPayments[m - 1];
            if (debt <= 0) {
                portfolio += extra + mp;
                totalInvested += extra + mp;
                portfolio *= (1 + investMonthlyRate);
                continue;
            }

            double interest = debt * monthlyRates[m - 1];
            totalInterest += interest;

            double principalPaid = mp - interest;
            if (principalPaid >= debt) {
                totalPaid += debt + interest;
                debt = 0;
                monthsToPayOff = m;
            } else {
                debt -= principalPaid;
                totalPaid += mp;
            }

            portfolio += extra;
            totalInvested += extra;
            portfolio *= (1 + investMonthlyRate);
        }

        double gains = portfolio - totalInvested;
        double tax = gains > 0 ? gains * taxRate / 100.0 : 0;
        double portfolioAfterTax = portfolio - tax;

        return StrategyResult.builder()
                .name("Invertir")
                .totalInterestPaid(round2(totalInterest))
                .totalInvested(round2(totalInvested))
                .finalPortfolioValue(round2(portfolio))
                .finalPortfolioAfterTax(round2(portfolioAfterTax))
                .netWealthEnd(round2(portfolioAfterTax - debt))
                .monthsToPayOff(monthsToPayOff)
                .totalPaidToBank(round2(totalPaid))
                .build();
    }

    private List<YearSnapshot> buildEvolution(double principal, double[] monthlyRates, double[] monthlyPayments,
                                              double extra, int years, double portfolioStart, double investReturn) {
        List<YearSnapshot> result = new ArrayList<>();
        double debt = principal;
        double portfolio = portfolioStart;
        double investMonthlyRate = investReturn / 100.0 / 12.0;
        double cumulativeInterest = 0;
        int monthIdx = 0;

        for (int y = 1; y <= years; y++) {
            for (int m = 0; m < 12; m++) {
                if (debt > 0) {
                    double interest = debt * monthlyRates[monthIdx];
                    cumulativeInterest += interest;
                    double principalPaid = monthlyPayments[monthIdx] - interest;
                    if (principalPaid >= debt) { debt = 0; } else { debt -= principalPaid; }
                }
                portfolio += extra;
                portfolio *= (1 + investMonthlyRate);
                monthIdx++;
            }
            result.add(YearSnapshot.builder()
                    .year(y)
                    .debtRemaining(round2(debt))
                    .portfolioValue(round2(portfolio))
                    .netWealth(round2(portfolio - debt))
                    .cumulativeInterestPaid(round2(cumulativeInterest))
                    .build());
        }
        return result;
    }

    private List<SensitivityRow> buildSensitivity(double principal, double annualRate,
                                                   double extra, int years, double portfolioStart,
                                                   double investedStart, double taxRate) {
        List<SensitivityRow> rows = new ArrayList<>();
        double[] returns = {3, 5, 7, 9, 12};
        double[] rates = {annualRate - 1, annualRate, annualRate + 1, annualRate + 2};

        for (double r : returns) {
            for (double rate : rates) {
                if (rate < 0) continue;
                double mp = calculateMonthlyPayment(principal, rate, years);
                int totalMonths = years * 12;
                double monthlyRate = rate / 100.0 / 12.0;
                double[] fixedRates = new double[totalMonths];
                double[] fixedPayments = new double[totalMonths];
                for (int i = 0; i < totalMonths; i++) { fixedRates[i] = monthlyRate; fixedPayments[i] = mp; }

                StrategyResult amort = simulateAmortize(principal, fixedRates, fixedPayments, extra, years, portfolioStart, investedStart, r, taxRate);
                StrategyResult invest = simulateInvest(principal, fixedRates, fixedPayments, extra, years, portfolioStart, investedStart, r, taxRate);

                String winner = invest.getNetWealthEnd() > amort.getNetWealthEnd() ? "INVERTIR" : "AMORTIZAR";
                double adv = Math.abs(invest.getNetWealthEnd() - amort.getNetWealthEnd());

                rows.add(SensitivityRow.builder()
                        .investmentReturn(r)
                        .mortgageRate(round2(rate))
                        .winner(winner)
                        .advantageEur(round2(adv))
                        .build());
            }
        }
        return rows;
    }

    /**
     * Builds monthly payment schedule. For mixed mortgages, recalculates the payment
     * at each period transition based on remaining principal and remaining term.
     */
    private double[] buildMonthlyPaymentSchedule(MortgageRequest req, double principal, double initialPayment, int remainingYears) {
        int totalMonths = remainingYears * 12;
        double[] payments = new double[totalMonths];

        if (req.getRatePeriods() == null || req.getRatePeriods().size() <= 1) {
            // Fixed rate or single period: same payment throughout
            for (int i = 0; i < totalMonths; i++) payments[i] = initialPayment;
            return payments;
        }

        // Mixed: simulate month by month, recalculating payment at period boundaries
        double debt = principal;
        int monthIdx = 0;
        int monthsElapsed = 0;

        for (int p = 0; p < req.getRatePeriods().size() && monthIdx < totalMonths; p++) {
            RatePeriod period = req.getRatePeriods().get(p);
            double annualRate = period.getRate();
            double monthlyRate = annualRate / 100.0 / 12.0;
            int periodMonths = period.getYears() * 12;
            int remainingMonths = totalMonths - monthsElapsed;

            // Recalculate payment for this period based on current debt and remaining term
            double periodPayment = calculateMonthlyPayment(debt, annualRate, (totalMonths - monthsElapsed + 11) / 12);
            // More precise: use remaining months directly
            if (monthlyRate > 0) {
                int n = totalMonths - monthsElapsed;
                periodPayment = debt * monthlyRate * Math.pow(1 + monthlyRate, n) / (Math.pow(1 + monthlyRate, n) - 1);
            } else {
                periodPayment = debt / (totalMonths - monthsElapsed);
            }

            for (int m = 0; m < periodMonths && monthIdx < totalMonths; m++) {
                payments[monthIdx] = periodPayment;
                // Advance debt for accurate recalculation at next period
                double interest = debt * monthlyRate;
                double principalPaid = periodPayment - interest;
                if (principalPaid > debt) principalPaid = debt;
                debt -= principalPaid;
                if (debt < 0) debt = 0;
                monthIdx++;
                monthsElapsed++;
            }
        }

        // Fill any remaining months (shouldn't happen if periods cover all years)
        if (monthIdx < totalMonths && monthIdx > 0) {
            for (; monthIdx < totalMonths; monthIdx++) payments[monthIdx] = payments[monthIdx - 1];
        }

        return payments;
    }

    private double calculateMonthlyPayment(double principal, double annualRate, int years) {
        if (annualRate <= 0) return principal / (years * 12.0);
        double monthlyRate = annualRate / 100.0 / 12.0;
        int n = years * 12;
        return principal * monthlyRate * Math.pow(1 + monthlyRate, n) / (Math.pow(1 + monthlyRate, n) - 1);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

