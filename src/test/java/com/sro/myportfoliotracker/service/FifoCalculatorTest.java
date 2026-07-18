package com.sro.myportfoliotracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sro.myportfoliotracker.model.DcaEntry;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FifoCalculatorTest {

  @Test
  void noSells_averageCost() {
    // 10sh@10€, 20sh@20€ → avg = (100+400)/30 = 16.667
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 20.0, 20.0)
    );
    FifoCalculator.FifoResult r = FifoCalculator.calculate(entries);
    assertEquals(30.0, r.remainingShares(), 0.001);
    assertEquals(16.6667, r.avgPrice(), 0.01);
    assertEquals(0.0, r.realizedPL(), 0.001);
  }

  @Test
  void partialSell_fifoRemaining() {
    // BUYs: 10sh@10€, 20sh@20€ → SELL 15sh a 25€
    // FIFO: 10 del 1er lote (10€) + 5 del 2º lote (20€)
    // remaining: 15sh del 2º lote (20€) → avgPrice = 20€
    // realizedPL: 10*(25-10) + 5*(25-20) = 150 + 25 = 175
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 20.0, 20.0),
        buildSell(3L, "TICK", LocalDate.of(2025, 3, 1), 15.0, 25.0)
    );
    FifoCalculator.FifoResult r = FifoCalculator.calculate(entries);
    assertEquals(15.0, r.remainingShares(), 0.001);
    assertEquals(20.0, r.avgPrice(), 0.001);
    assertEquals(175.0, r.realizedPL(), 0.001);
  }

  @Test
  void fullSell_zeroPosition() {
    // BUYs: 10sh@10€, 20sh@20€ → SELL 30sh a 25€
    // remaining: 0sh, avgPrice = 0
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 20.0, 20.0),
        buildSell(3L, "TICK", LocalDate.of(2025, 3, 1), 30.0, 25.0)
    );
    FifoCalculator.FifoResult r = FifoCalculator.calculate(entries);
    assertEquals(0.0, r.remainingShares(), 0.001);
    assertEquals(0.0, r.avgPrice(), 0.001);
  }

  @Test
  void multipleSells_fifoAccumulatesRL() {
    // BUYs: 10sh@10€, 20sh@15€, 30sh@20€
    // SELL 15sh → 10 del 1º (10€) + 5 del 2º (15€)
    // SELL 20sh → 15 del 2º restantes (15€) + 5 del 3º (20€)
    // remaining: 25sh del 3er lote (20€) → avgPrice = 20€
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 20.0, 15.0),
        buildBuy(3L, "TICK", LocalDate.of(2025, 3, 1), 30.0, 20.0),
        buildSell(4L, "TICK", LocalDate.of(2025, 4, 1), 15.0, 22.0),
        buildSell(5L, "TICK", LocalDate.of(2025, 5, 1), 20.0, 25.0)
    );
    FifoCalculator.FifoResult r = FifoCalculator.calculate(entries);
    assertEquals(25.0, r.remainingShares(), 0.001);
    assertEquals(20.0, r.avgPrice(), 0.001);
    // 1ª venta: 10*(22-10) + 5*(22-15) = 120 + 35 = 155
    // 2ª venta: 15*(25-15) + 5*(25-20) = 150 + 25 = 175
    // total = 330
    assertEquals(330.0, r.realizedPL(), 0.001);
  }

  @Test
  void sellConsumesMultipleLots() {
    // BUYs: 10sh@10€, 5sh@12€, 8sh@14€ → SELL 20sh
    // Quedan 3sh del 3er lote (14€) → avg = 14€
    List<DcaEntry> entries = List.of(
        buildBuy(1L, "TICK", LocalDate.of(2025, 1, 1), 10.0, 10.0),
        buildBuy(2L, "TICK", LocalDate.of(2025, 2, 1), 5.0, 12.0),
        buildBuy(3L, "TICK", LocalDate.of(2025, 3, 1), 8.0, 14.0),
        buildSell(4L, "TICK", LocalDate.of(2025, 4, 1), 20.0, 18.0)
    );
    FifoCalculator.FifoResult r = FifoCalculator.calculate(entries);
    assertEquals(3.0, r.remainingShares(), 0.001);
    assertEquals(14.0, r.avgPrice(), 0.001);
  }

  @Test
  void emptyEntries_zeroResult() {
    List<DcaEntry> entries = List.of();
    FifoCalculator.FifoResult r = FifoCalculator.calculate(entries);
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
