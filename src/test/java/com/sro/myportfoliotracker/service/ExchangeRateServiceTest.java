package com.sro.myportfoliotracker.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeRateServiceTest {

    /**
     * Verifica que EUR → EUR devuelve el mismo precio sin necesidad de tasas cargadas.
     */
    @Test
    void convertToEur_eurCurrency_returnsSamePrice() {
        // ExchangeRateService requires RestClient + ObjectMapper, but EUR conversion
        // is a pure function that doesn't hit the API.
        // We create a testable instance using a minimal approach.
        var service = new ExchangeRateService(null, null);
        double result = service.convertToEur(100.0, "EUR");
        assertEquals(100.0, result, 0.001);
    }

    @Test
    void convertToEur_eurCaseInsensitive() {
        var service = new ExchangeRateService(null, null);
        assertEquals(50.0, service.convertToEur(50.0, "eur"), 0.001);
        assertEquals(50.0, service.convertToEur(50.0, "Eur"), 0.001);
    }

    @Test
    void getRate_eurReturnsOne() {
        var service = new ExchangeRateService(null, null);
        assertEquals(1.0, service.getRate("EUR"), 0.001);
    }
}

