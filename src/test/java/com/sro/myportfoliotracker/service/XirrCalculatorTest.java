package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.service.XirrCalculator.CashFlow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XirrCalculatorTest {

    @Test
    void nullOrEmpty_returnsNull() {
        assertNull(XirrCalculator.calculate(null));
        assertNull(XirrCalculator.calculate(List.of()));
    }

    @Test
    void singleFlow_returnsNull() {
        assertNull(XirrCalculator.calculate(List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -1000)
        )));
    }

    @Test
    void onlyPositiveFlows_returnsNull() {
        assertNull(XirrCalculator.calculate(List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), 500),
                new CashFlow(LocalDate.of(2024, 6, 1), 600)
        )));
    }

    @Test
    void onlyNegativeFlows_returnsNull() {
        assertNull(XirrCalculator.calculate(List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -500),
                new CashFlow(LocalDate.of(2024, 6, 1), -600)
        )));
    }

    @Test
    void simpleInvestment_positiveReturn() {
        // Invest 1000, get 1100 after 1 year ≈ 10% return
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -1000),
                new CashFlow(LocalDate.of(2025, 1, 1), 1100)
        );
        Double xirr = XirrCalculator.calculate(flows);
        assertNotNull(xirr);
        assertEquals(0.10, xirr, 0.01);
    }

    @Test
    void simpleInvestment_negativeReturn() {
        // Invest 1000, get 900 after 1 year ≈ -10% return
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -1000),
                new CashFlow(LocalDate.of(2025, 1, 1), 900)
        );
        Double xirr = XirrCalculator.calculate(flows);
        assertNotNull(xirr);
        assertEquals(-0.10, xirr, 0.01);
    }

    @Test
    void dcaInvestment_multipleFlows() {
        // Two DCA purchases + final value
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -500),
                new CashFlow(LocalDate.of(2024, 7, 1), -500),
                new CashFlow(LocalDate.of(2025, 1, 1), 1100)
        );
        Double xirr = XirrCalculator.calculate(flows);
        assertNotNull(xirr);
        assertTrue(xirr > 0, "Expected positive XIRR for profitable DCA");
    }

    @Test
    void breakEven_returnsNearZero() {
        List<CashFlow> flows = List.of(
                new CashFlow(LocalDate.of(2024, 1, 1), -1000),
                new CashFlow(LocalDate.of(2025, 1, 1), 1000)
        );
        Double xirr = XirrCalculator.calculate(flows);
        assertNotNull(xirr);
        assertEquals(0.0, xirr, 0.01);
    }
}

