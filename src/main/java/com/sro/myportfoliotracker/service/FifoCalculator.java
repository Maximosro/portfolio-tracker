package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.DcaEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calculadora de cost basis por método FIFO (First-In, First-Out).
 * <p>
 * Procesa entradas DCA en orden cronológico: las compras añaden lotes,
 * las ventas consumen los lotes más antiguos primero. El resultado incluye
 * las shares restantes, su precio medio y el P&amp;L realizado acumulado.
 */
public final class FifoCalculator {

  private FifoCalculator() {
    // utilidad estática
  }

  public record FifoResult(double remainingShares, double avgPrice, double realizedPL) {

  }

  /**
   * Calcula posición y P&amp;L realizado usando FIFO.
   *
   * @param entries entradas DCA (BUY y SELL) — se ordenan por fecha ASC, ID ASC internamente
   * @return resultado con shares restantes, avgPrice y realizedPL acumulado
   */
  public static FifoResult calculate(List<DcaEntry> entries) {
    // Ordenar por fecha ASC, ID ASC (tiebreaker)
    List<DcaEntry> sorted = new ArrayList<>(entries);
    sorted.sort(Comparator.comparing(DcaEntry::getDate).thenComparing(DcaEntry::getId));

    List<Lot> lots = new ArrayList<>();
    double realizedPL = 0.0;

    for (DcaEntry e : sorted) {
      if ("SELL".equals(e.getType())) {
        double remaining = e.getShares();
        while (remaining > 0 && !lots.isEmpty()) {
          Lot first = lots.get(0); // FIFO: primer lote
          double consumed = Math.min(first.shares, remaining);
          realizedPL += consumed * (e.getPrice() - first.price);
          first.shares -= consumed;
          remaining -= consumed;
          if (first.shares <= 0) {
            lots.remove(0);
          }
        }
        // si remaining > 0 y lots vacío: se vendieron más shares de las compradas
        // (no debería ocurrir, DcaService lo valida antes)
      } else {
        // BUY
        lots.add(new Lot(e.getShares(), e.getPrice()));
      }
    }

    double totalShares = lots.stream().mapToDouble(l -> l.shares).sum();
    double totalCost = lots.stream().mapToDouble(l -> l.shares * l.price).sum();
    double avgPrice = totalShares > 0 ? totalCost / totalShares : 0.0;

    return new FifoResult(
        totalShares,
        avgPrice,
        Math.round(realizedPL * 100.0) / 100.0);
  }

  /**
   * Computes the FIFO cost basis per share for a SELL operation.
   * <p>
   * Builds lots from all BUY entries in {@code entriesUpToNow}, consumes previous
   * SELLs from those lots (oldest first), then simulates consuming {@code sellShares}
   * from the remaining lots.
   *
   * @param entriesUpToNow all DCA entries (BUY+SELL) existing BEFORE this sale,
   *                       sorted by date ASC
   * @param sellShares     number of shares to sell
   * @return weighted average price of the FIFO-consumed shares, or 0 if no lots
   */
  public static double computeSellCostBasis(List<DcaEntry> entriesUpToNow, double sellShares) {
    // Sort by date ASC, ID ASC (tiebreaker) — same as calculate()
    List<DcaEntry> sorted = new ArrayList<>(entriesUpToNow);
    sorted.sort(Comparator.comparing(DcaEntry::getDate).thenComparing(DcaEntry::getId));

    // Build lots from all BUY entries
    List<Lot> lots = new ArrayList<>();
    for (DcaEntry e : sorted) {
      if (!"SELL".equals(e.getType())) {
        lots.add(new Lot(e.getShares(), e.getPrice()));
      }
    }

    // Consume previous SELLs from lots (FIFO)
    for (DcaEntry e : sorted) {
      if ("SELL".equals(e.getType())) {
        double remaining = e.getShares();
        while (remaining > 0 && !lots.isEmpty()) {
          Lot first = lots.get(0);
          double consumed = Math.min(first.shares, remaining);
          first.shares -= consumed;
          remaining -= consumed;
          if (first.shares <= 0) {
            lots.remove(0);
          }
        }
      }
    }

    // Simulate consuming sellShares from remaining lots
    double totalCost = 0;
    double remaining = sellShares;
    while (remaining > 0 && !lots.isEmpty()) {
      Lot first = lots.get(0);
      double consumed = Math.min(first.shares, remaining);
      totalCost += consumed * first.price;
      first.shares -= consumed;
      remaining -= consumed;
      if (first.shares <= 0) {
        lots.remove(0);
      }
    }

    double consumedShares = sellShares - remaining;
    return consumedShares > 0 ? totalCost / consumedShares : 0;
  }

  private static class Lot {

    double shares;
    final double price;

    Lot(double shares, double price) {
      this.shares = shares;
      this.price = price;
    }
  }
}
