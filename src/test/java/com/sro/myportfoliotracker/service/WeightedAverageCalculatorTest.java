package com.sro.myportfoliotracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sro.myportfoliotracker.model.DcaEntry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedAverageCalculatorTest {

  @Test
  void noSells_averageCost() {
    // 10sh@10€, 20sh@20€ → avg = (100+400)/30 = 16.667
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 20.0, 20.0)
    );
    WeightedAverageCalculator.WacResult r = WeightedAverageCalculator.calculate(entries);
    assertEquals(30.0, r.remainingShares(), 0.001);
    assertEquals(16.6667, r.avgPrice(), 0.01);
    assertEquals(0.0, r.realizedPL(), 0.001);
  }

  @Test
  void partialSell_wacConstantAvg() {
    // BUYs: 10sh@10€, 20sh@20€ → SELL 15sh a 25€
    // WAC: avg = (10*10+20*20)/30 = 16.667
    // realizedPL = 15*(25-16.667) = 125.0
    // After sell: shares=15, avgPrice stays 16.667 (WAC: avg doesn't change)
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 20.0, 20.0),
        buildSell(3L, "TICK", LocalDate.of(2025, 3, 1), 15.0, 25.0)
    );
    WeightedAverageCalculator.WacResult r = WeightedAverageCalculator.calculate(entries);
    assertEquals(15.0, r.remainingShares(), 0.001);
    // WAC: avg stays at the weighted average of all buys, unchanged by sells
    assertEquals(16.667, r.avgPrice(), 0.01);
    assertEquals(125.0, r.realizedPL(), 0.01);
  }

  @Test
  void fullSell_zeroPosition() {
    // BUYs: 10sh@10€, 20sh@20€ → SELL 30sh a 25€
    // WAC: avg = 16.667, realizedPL = 30*(25-16.667) = 250.0
    // remaining: 0sh, avgPrice = 0
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 20.0, 20.0),
        buildSell(3L, "TICK", LocalDate.of(2025, 3, 1), 30.0, 25.0)
    );
    WeightedAverageCalculator.WacResult r = WeightedAverageCalculator.calculate(entries);
    assertEquals(0.0, r.remainingShares(), 0.001);
    assertEquals(0.0, r.avgPrice(), 0.001);
  }

  @Test
  void multipleSells_wacConstantAvg() {
    // BUYs: 10sh@10€, 20sh@15€, 30sh@20€
    // WAC after buys: shares=60, totalCost=100+300+600=1000, avg=16.667
    // SELL 15sh@22€: realizedPL = 15*(22-16.667) = 80.0
    // SELL 20sh@25€: realizedPL = 20*(25-16.667) = 166.667
    // Total realizedPL = 246.667, avg stays 16.667
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 20.0, 15.0),
        buildBuy(3L, "TICK", LocalDate.of(2025, 3, 1), 30.0, 20.0),
        buildSell(4L, "TICK", LocalDate.of(2025, 4, 1), 15.0, 22.0),
        buildSell(5L, "TICK", LocalDate.of(2025, 5, 1), 20.0, 25.0)
    );
    WeightedAverageCalculator.WacResult r = WeightedAverageCalculator.calculate(entries);
    assertEquals(25.0, r.remainingShares(), 0.001);
    // WAC: avg is constant through sells
    assertEquals(16.667, r.avgPrice(), 0.01);
    // 80.0 + 166.667 = 246.667 ≈ 246.67
    assertEquals(246.67, r.realizedPL(), 0.01);
  }

  @Test
  void sellConsumesMultipleLots_wacConstantAvg() {
    // BUYs: 10sh@10€, 5sh@12€, 8sh@14€ → SELL 20sh@18€
    // WAC: shares=23, totalCost=100+60+112=272, avg=272/23=11.826
    // realizedPL = 20*(18-11.826) = 123.48
    // remaining: 3sh, avgPrice stays 11.826
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 5.0, 12.0),
        buildBuy(3L, "TICK", LocalDate.of(2025, 3, 1), 8.0, 14.0),
        buildSell(4L, "TICK", LocalDate.of(2025, 4, 1), 20.0, 18.0)
    );
    WeightedAverageCalculator.WacResult r = WeightedAverageCalculator.calculate(entries);
    assertEquals(3.0, r.remainingShares(), 0.001);
    assertEquals(11.826, r.avgPrice(), 0.01);
    assertEquals(123.48, r.realizedPL(), 0.01);
  }

  @Test
  void emptyEntries_zeroResult() {
    List<DcaEntry> entries = List.of();
    WeightedAverageCalculator.WacResult r = WeightedAverageCalculator.calculate(entries);
    assertEquals(0.0, r.remainingShares(), 0.001);
    assertEquals(0.0, r.avgPrice(), 0.001);
    assertEquals(0.0, r.realizedPL(), 0.001);
  }

  // ═══════════════════════════════════════════
  // Helpers
  // ═══════════════════════════════════════════
  private static DcaEntry buildBuy(Long id, String ticker, LocalDate date, double shares,
      double price) {
    DcaEntry e = new DcaEntry();
    e.setId(id);
    e.setTicker(ticker);
    e.setDate(date);
    e.setShares(shares);
    e.setPrice(price);
    e.setType("BUY");
    return e;
  }

  private static DcaEntry buildSell(Long id, String ticker, LocalDate date, double shares,
      double price) {
    DcaEntry e = new DcaEntry();
    e.setId(id);
    e.setTicker(ticker);
    e.setDate(date);
    e.setShares(shares);
    e.setPrice(price);
    e.setType("SELL");
    return e;
  }
}
