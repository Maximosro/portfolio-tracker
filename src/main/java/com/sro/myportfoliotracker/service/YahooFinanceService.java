package com.sro.myportfoliotracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sro.myportfoliotracker.dto.YahooQuote;
import com.sro.myportfoliotracker.dto.YahooQuoteExtended;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class YahooFinanceService {

    private static final String YAHOO_URL = "https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?range=1d&interval=1d";
    private static final String YAHOO_URL_1MO = "https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?range=1mo&interval=1d";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /**
     * Obtiene el precio actual y la divisa de un ticker de Yahoo Finance.
     */
    public YahooQuote fetchQuote(String yahooTicker) {
        try {
            String body = restClient.get()
                    .uri(YAHOO_URL, yahooTicker)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new RuntimeException("Respuesta vacía de Yahoo Finance para " + yahooTicker);
            }

            JsonNode response = objectMapper.readTree(body);
            JsonNode result = response.path("chart").path("result");

            if (result.isMissingNode() || !result.isArray() || result.isEmpty()) {
                throw new RuntimeException("Sin datos de Yahoo Finance para " + yahooTicker);
            }

            JsonNode meta = result.get(0).path("meta");
            double price = meta.path("regularMarketPrice").asDouble();
            String currency = meta.path("currency").asText("USD");

            if (price <= 0) {
                throw new RuntimeException("Precio inválido (0) para " + yahooTicker);
            }

            return new YahooQuote(price, currency);

        } catch (Exception e) {
            log.error("Error obteniendo precio de {}: {}", yahooTicker, e.getMessage());
            throw new RuntimeException("Error fetching " + yahooTicker + ": " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene precio actual + variaciones diaria/semanal/mensual con una sola llamada (range=1mo).
     */
    public YahooQuoteExtended fetchQuoteExtended(String yahooTicker) {
        try {
            String body = restClient.get()
                    .uri(YAHOO_URL_1MO, yahooTicker)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new RuntimeException("Respuesta vacía de Yahoo Finance para " + yahooTicker);
            }

            JsonNode response = objectMapper.readTree(body);
            JsonNode result = response.path("chart").path("result");

            if (result.isMissingNode() || !result.isArray() || result.isEmpty()) {
                throw new RuntimeException("Sin datos de Yahoo Finance para " + yahooTicker);
            }

            JsonNode meta = result.get(0).path("meta");
            double currentPrice = meta.path("regularMarketPrice").asDouble();
            String currency = meta.path("currency").asText("USD");

            if (currentPrice <= 0) {
                throw new RuntimeException("Precio inválido (0) para " + yahooTicker);
            }

            // previousClose del meta = cierre del día anterior (correcto para daily)
            // chartPreviousClose = cierre ANTES del inicio del rango (1 mes atrás) — NO usar para daily
            double previousDayClose = meta.path("previousClose").asDouble(0);

            // Extraer array de precios de cierre del último mes
            JsonNode closePrices = result.get(0).path("indicators").path("quote");
            double[] closes = null;
            if (!closePrices.isMissingNode() && closePrices.isArray() && !closePrices.isEmpty()) {
                JsonNode closeArray = closePrices.get(0).path("close");
                if (!closeArray.isMissingNode() && closeArray.isArray()) {
                    int total = closeArray.size();
                    double[] raw = new double[total];
                    int validCount = 0;
                    for (int i = 0; i < total; i++) {
                        if (!closeArray.get(i).isNull()) {
                            raw[validCount++] = closeArray.get(i).asDouble();
                        }
                    }
                    closes = java.util.Arrays.copyOf(raw, validCount);
                }
            }

            // Fallback: si previousClose no está en meta, usar penúltimo cierre del array
            if (previousDayClose <= 0 && closes != null && closes.length >= 2) {
                previousDayClose = closes[closes.length - 2];
            }

            // Calcular variaciones
            Double changePctDay = previousDayClose > 0
                    ? Math.round(((currentPrice - previousDayClose) / previousDayClose) * 10000.0) / 100.0
                    : null;

            Double changePctWeek = null;
            Double changePctMonth = null;

            if (closes != null && closes.length > 0) {
                // Semanal: ~5 sesiones atrás desde el último cierre
                int weekIdx = Math.max(0, closes.length - 6);
                if (closes[weekIdx] > 0) {
                    changePctWeek = Math.round(((currentPrice - closes[weekIdx]) / closes[weekIdx]) * 10000.0) / 100.0;
                }
                // Mensual: primer precio disponible del rango
                if (closes[0] > 0) {
                    changePctMonth = Math.round(((currentPrice - closes[0]) / closes[0]) * 10000.0) / 100.0;
                }
            }

            return new YahooQuoteExtended(currentPrice, currency, changePctDay, changePctWeek, changePctMonth);

        } catch (Exception e) {
            log.error("Error obteniendo cotización extendida de {}: {}", yahooTicker, e.getMessage());
            throw new RuntimeException("Error fetching extended " + yahooTicker + ": " + e.getMessage(), e);
        }
    }
}
