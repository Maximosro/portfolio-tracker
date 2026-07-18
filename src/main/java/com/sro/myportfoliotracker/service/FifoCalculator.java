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
   * Computes the average cost basis per share for a SELL operation.
   * <p>
   * Uses the blended average price of all BUY entries that exist at the time
   * of the sale ({@code entriesUpToNow}), matching the method used by brokers
   * like Trade Republic (Durchschnittskursverfahren).
   * <p>
   * This is NOT FIFO — it's the simple average of all purchase prices up to
   * the sale date. The cost basis is computed once and stored immutably.
   *
   * @param entriesUpToNow all DCA entries (BUY+SELL) existing BEFORE this sale,
   *                       sorted by date ASC
   * @param sellShares     number of shares to sell (ignored — the average is per-share)
   * @return blended average price of all BUY entries up to now, or 0 if no buys
   */
  public static double computeSellCostBasis(List<DcaEntry> entriesUpToNow, double sellShares) {
    double buyShares = 0.0;
    double buyCost = 0.0;
    for (DcaEntry e : entriesUpToNow) {
      if (!"SELL".equals(e.getType())) {
        buyShares += e.getShares();
        buyCost += e.getShares() * e.getPrice();
      }
    }
    return buyShares > 0 ? buyCost / buyShares : 0.0;
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
