package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.DcaEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calculadora de cost basis por método de precio medio ponderado (Weighted Average Cost).
 * <p>
 * Procesa entradas DCA en orden cronológico: cada compra incrementa el número de
 * acciones y el coste total, actualizando el precio medio. Cada venta usa el precio
 * medio actual como cost basis y reduce acciones y coste proporcionalmente.
 * El precio medio NO cambia tras una venta parcial.
 * <p>
 * Este es el método utilizado por Trade Republic.
 */
public final class WeightedAverageCalculator {

  private WeightedAverageCalculator() {
    // utilidad estática
  }

  public record WacResult(double remainingShares, double avgPrice, double realizedPL) {

  }

  /**
   * Calcula posición y P&amp;L realizado usando precio medio ponderado.
   *
   * @param entries entradas DCA (BUY y SELL) — se ordenan por fecha ASC, ID ASC internamente
   * @return resultado con shares restantes, avgPrice y realizedPL acumulado
   */
  public static WacResult calculate(List<DcaEntry> entries) {
    // Ordenar por fecha ASC, ID ASC (tiebreaker)
    List<DcaEntry> sorted = new ArrayList<>(entries);
    sorted.sort(Comparator.comparing(DcaEntry::getDate).thenComparing(DcaEntry::getId));

    double shares = 0.0;
    double totalCost = 0.0;
    double realizedPL = 0.0;

    for (DcaEntry e : sorted) {
      if ("SELL".equals(e.getType())) {
        double avgPrice = shares > 0 ? totalCost / shares : 0.0;
        realizedPL += e.getShares() * (e.getPrice() - avgPrice);
        shares -= e.getShares();
        totalCost -= e.getShares() * avgPrice;
      } else {
        // BUY
        shares += e.getShares();
        totalCost += e.getShares() * e.getPrice();
      }
    }

    double avgPrice = shares > 0 ? totalCost / shares : 0.0;

    return new WacResult(
        shares,
        avgPrice,
        Math.round(realizedPL * 100.0) / 100.0);
  }

  /**
   * Computes the WAC cost basis per share for a SELL operation.
   * <p>
   * With weighted average cost, the cost basis is simply the current average price
   * before this sale, independent of how many shares are sold. The {@code sellShares}
   * parameter is kept for API compatibility with the old {@code FifoCalculator} but
   * is not used in the calculation.
   *
   * @param entriesUpToNow all DCA entries (BUY+SELL) existing BEFORE this sale,
   *                       sorted internally by date ASC
   * @param sellShares     ignored — with WAC, cost basis = current average price
   * @return current weighted average price per share, or 0 if no shares held
   */
  public static double computeSellCostBasis(List<DcaEntry> entriesUpToNow, double sellShares) {
    // Sort by date ASC, ID ASC (tiebreaker) — same as calculate()
    List<DcaEntry> sorted = new ArrayList<>(entriesUpToNow);
    sorted.sort(Comparator.comparing(DcaEntry::getDate).thenComparing(DcaEntry::getId));

    double shares = 0.0;
    double totalCost = 0.0;

    for (DcaEntry e : sorted) {
      if ("SELL".equals(e.getType())) {
        double avgPrice = shares > 0 ? totalCost / shares : 0.0;
        shares -= e.getShares();
        totalCost -= e.getShares() * avgPrice;
      } else {
        // BUY
        shares += e.getShares();
        totalCost += e.getShares() * e.getPrice();
      }
    }

    return shares > 0 ? totalCost / shares : 0.0;
  }
}
