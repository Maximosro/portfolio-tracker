package com.sro.myportfoliotracker.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Calcula XIRR (Extended Internal Rate of Return) usando Newton-Raphson. Flujos negativos = compras
 * (dinero que sale). Flujo positivo final = valor actual de la posición (dinero que "entra").
 */
public final class XirrCalculator {

  private static final int MAX_ITERATIONS = 500;
  private static final double TOLERANCE = 1e-9;
  private static final double DEFAULT_GUESS = 0.1;

  private XirrCalculator() {
  }

  public record CashFlow(LocalDate date, double amount) {

  }

  /**
   * Calcula la tasa XIRR.
   *
   * @return tasa anualizada (ej. 0.12 = 12%) o null si no converge o datos insuficientes.
   */
  public static Double calculate(List<CashFlow> cashFlows) {
    if (cashFlows == null || cashFlows.size() < 2) {
      return null;
    }

    // Verificar que hay al menos un flujo positivo y uno negativo
    boolean hasPositive = cashFlows.stream().anyMatch(cf -> cf.amount() > 0);
    boolean hasNegative = cashFlows.stream().anyMatch(cf -> cf.amount() < 0);
    if (!hasPositive || !hasNegative) {
      return null;
    }

    LocalDate baseDate = cashFlows.getFirst().date();

    // Intentar Newton-Raphson
    Double result = newtonRaphson(cashFlows, baseDate, DEFAULT_GUESS);
    if (result != null) {
      return result;
    }

    // Fallback: bisección si Newton no converge
    return bisection(cashFlows, baseDate);
  }

  private static Double newtonRaphson(List<CashFlow> flows, LocalDate baseDate, double guess) {
    double rate = guess;

    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double npv = 0;
      double derivative = 0;

      for (CashFlow cf : flows) {
        double years = ChronoUnit.DAYS.between(baseDate, cf.date()) / 365.25;
        double factor = Math.pow(1 + rate, years);
        npv += cf.amount() / factor;
        derivative -= years * cf.amount() / (factor * (1 + rate));
      }

      if (Math.abs(derivative) < 1e-14) {
        break;
      }

      double newRate = rate - npv / derivative;

      if (Math.abs(newRate - rate) < TOLERANCE) {
        return Math.round(newRate * 10000.0) / 10000.0; // 4 decimales
      }

      rate = newRate;

      // Evitar divergencia
      if (rate < -0.999 || rate > 100) {
        break;
      }
    }

    return null; // No converge
  }

  private static Double bisection(List<CashFlow> flows, LocalDate baseDate) {
    double low = -0.99;
    double high = 10.0;

    double npvLow = npv(flows, baseDate, low);
    double npvHigh = npv(flows, baseDate, high);

    if (npvLow * npvHigh > 0) {
      return null; // No hay raíz en este rango
    }

    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double mid = (low + high) / 2;
      double npvMid = npv(flows, baseDate, mid);

      if (Math.abs(npvMid) < TOLERANCE || (high - low) < TOLERANCE) {
        return Math.round(mid * 10000.0) / 10000.0;
      }

      if (npvMid * npvLow < 0) {
        high = mid;
        npvHigh = npvMid;
      } else {
        low = mid;
        npvLow = npvMid;
      }
    }

    return null;
  }

  private static double npv(List<CashFlow> flows, LocalDate baseDate, double rate) {
    double sum = 0;
    for (CashFlow cf : flows) {
      double years = ChronoUnit.DAYS.between(baseDate, cf.date()) / 365.25;
      sum += cf.amount() / Math.pow(1 + rate, years);
    }
    return sum;
  }
}

