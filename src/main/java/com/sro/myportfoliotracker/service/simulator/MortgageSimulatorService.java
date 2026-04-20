package com.sro.myportfoliotracker.service.simulator;

import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.dto.simulator.MortgageRequest;
import com.sro.myportfoliotracker.dto.simulator.MortgageResult;
import com.sro.myportfoliotracker.dto.simulator.MortgageResult.*;
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
        double annualRate = req.getAnnualInterestRate();
        int remainingYears = req.getRemainingYears();
        double extra = req.getExtraMonthly() != null ? req.getExtraMonthly() : 0;
        double taxRate = req.getTaxRatePct() != null ? req.getTaxRatePct() : 21.0;

        // Calcular cuota mensual si no se proporciona
        double monthlyPayment;
        if (req.getMonthlyPayment() != null && req.getMonthlyPayment() > 0) {
            monthlyPayment = req.getMonthlyPayment();
        } else {
            monthlyPayment = calculateMonthlyPayment(principal, annualRate, remainingYears);
        }

        // ─── 1. ¿Cuándo puede la cartera pagar la hipoteca? (escenario: invertir el extra) ───
        Integer payoffMonth = null;
        Double payoffPortfolio = null;
        Double payoffDebt = null;
        {
            double debt = principal;
            double portfolio = currentPortfolio;
            double monthlyRate = annualRate / 100.0 / 12.0;
            double investMonthlyRate = investReturn / 100.0 / 12.0;
            int maxMonths = remainingYears * 12;

            for (int m = 1; m <= maxMonths; m++) {
                // Deuda se reduce con la cuota normal
                double interest = debt * monthlyRate;
                double principalPaid = monthlyPayment - interest;
                if (principalPaid > debt) principalPaid = debt;
                debt -= principalPaid;
                if (debt < 0) debt = 0;

                // Cartera crece con el extra + rentabilidad
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

        // ─── 2. Estrategia AMORTIZAR: extra va a reducir hipoteca ───
        StrategyResult amortizeResult = simulateAmortize(principal, annualRate, monthlyPayment, extra, remainingYears, currentPortfolio, currentInvested, investReturn, taxRate);

        // ─── 3. Estrategia INVERTIR: extra va a la cartera ───
        StrategyResult investResult = simulateInvest(principal, annualRate, monthlyPayment, extra, remainingYears, currentPortfolio, currentInvested, investReturn, taxRate);

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
        List<YearSnapshot> evolution = buildEvolution(principal, annualRate, monthlyPayment, extra, remainingYears, currentPortfolio, investReturn);

        // ─── 5. Sensibilidad ───
        List<SensitivityRow> sensitivity = buildSensitivity(principal, annualRate, monthlyPayment, extra, remainingYears, currentPortfolio, currentInvested, taxRate);

        return MortgageResult.builder()
                .outstandingPrincipal(principal)
                .annualInterestRate(annualRate)
                .remainingYears(remainingYears)
                .monthlyPayment(round2(monthlyPayment))
                .extraMonthly(extra)
                .investmentReturnPct(round2(investReturn))
                .investmentReturnSource(returnSource)
                .currentPortfolioValue(round2(currentPortfolio))
                .taxRatePct(taxRate)
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
     * Estrategia AMORTIZAR: el extra mensual reduce capital de hipoteca.
     * Una vez pagada la hipoteca, el dinero liberado (cuota + extra) va a inversión.
     */
    private StrategyResult simulateAmortize(double principal, double annualRate, double monthlyPayment,
                                            double extra, int years, double portfolioStart, double investedStart,
                                            double investReturn, double taxRate) {
        double debt = principal;
        double portfolio = portfolioStart;
        double totalInvested = investedStart;
        double monthlyRate = annualRate / 100.0 / 12.0;
        double investMonthlyRate = investReturn / 100.0 / 12.0;
        int maxMonths = years * 12;
        double totalInterest = 0;
        double totalPaid = 0;
        int monthsToPayOff = maxMonths;

        for (int m = 1; m <= maxMonths; m++) {
            if (debt <= 0) {
                // Hipoteca ya pagada: cuota + extra van a inversión
                double freeAmount = monthlyPayment + extra;
                portfolio += freeAmount;
                totalInvested += freeAmount;
                portfolio *= (1 + investMonthlyRate);
                continue;
            }

            double interest = debt * monthlyRate;
            totalInterest += interest;

            double principalPaid = monthlyPayment - interest + extra;
            if (principalPaid > debt) {
                totalPaid += debt + interest;
                debt = 0;
                monthsToPayOff = m;
            } else {
                debt -= principalPaid;
                totalPaid += monthlyPayment + extra;
            }

            // Cartera crece por rentabilidad solamente (no hay aportaciones mientras pagas hipoteca)
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

    /**
     * Estrategia INVERTIR: el extra mensual va a la cartera.
     * La hipoteca se paga normalmente con su cuota.
     */
    private StrategyResult simulateInvest(double principal, double annualRate, double monthlyPayment,
                                          double extra, int years, double portfolioStart, double investedStart,
                                          double investReturn, double taxRate) {
        double debt = principal;
        double portfolio = portfolioStart;
        double totalInvested = investedStart;
        double monthlyRate = annualRate / 100.0 / 12.0;
        double investMonthlyRate = investReturn / 100.0 / 12.0;
        int maxMonths = years * 12;
        double totalInterest = 0;
        double totalPaid = 0;
        int monthsToPayOff = maxMonths;

        for (int m = 1; m <= maxMonths; m++) {
            if (debt <= 0) {
                // Hipoteca ya pagada, todo el extra + cuota va a inversión
                portfolio += extra + monthlyPayment;
                totalInvested += extra + monthlyPayment;
                portfolio *= (1 + investMonthlyRate);
                continue;
            }

            double interest = debt * monthlyRate;
            totalInterest += interest;

            double principalPaid = monthlyPayment - interest;
            if (principalPaid >= debt) {
                totalPaid += debt + interest;
                debt = 0;
                monthsToPayOff = m;
            } else {
                debt -= principalPaid;
                totalPaid += monthlyPayment;
            }

            // Extra va a inversión
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

    private List<YearSnapshot> buildEvolution(double principal, double annualRate, double monthlyPayment,
                                              double extra, int years, double portfolioStart, double investReturn) {
        List<YearSnapshot> result = new ArrayList<>();
        double debt = principal;
        double portfolio = portfolioStart;
        double monthlyRate = annualRate / 100.0 / 12.0;
        double investMonthlyRate = investReturn / 100.0 / 12.0;
        double cumulativeInterest = 0;

        for (int y = 1; y <= years; y++) {
            for (int m = 0; m < 12; m++) {
                if (debt > 0) {
                    double interest = debt * monthlyRate;
                    cumulativeInterest += interest;
                    double principalPaid = monthlyPayment - interest;
                    if (principalPaid >= debt) { debt = 0; } else { debt -= principalPaid; }
                }
                // Invertir el extra
                portfolio += extra;
                portfolio *= (1 + investMonthlyRate);
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

    private List<SensitivityRow> buildSensitivity(double principal, double annualRate, double monthlyPayment,
                                                   double extra, int years, double portfolioStart,
                                                   double investedStart, double taxRate) {
        List<SensitivityRow> rows = new ArrayList<>();
        double[] returns = {3, 5, 7, 9, 12};
        double[] rates = {annualRate - 1, annualRate, annualRate + 1, annualRate + 2};

        for (double r : returns) {
            for (double rate : rates) {
                if (rate < 0) continue;
                double mp = calculateMonthlyPayment(principal, rate, years);
                StrategyResult amort = simulateAmortize(principal, rate, mp, extra, years, portfolioStart, investedStart, r, taxRate);
                StrategyResult invest = simulateInvest(principal, rate, mp, extra, years, portfolioStart, investedStart, r, taxRate);

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

