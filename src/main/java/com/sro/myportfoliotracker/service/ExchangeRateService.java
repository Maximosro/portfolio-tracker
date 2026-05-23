package com.sro.myportfoliotracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

  private static final String RATES_URL = "https://open.er-api.com/v6/latest/EUR";
  private static final long CACHE_TTL_SECONDS = 6 * 3600; // 6 horas

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final AtomicReference<Map<String, Double>> cachedRates = new AtomicReference<>(
      new ConcurrentHashMap<>());
  private final AtomicReference<Instant> lastFetch = new AtomicReference<>(Instant.EPOCH);

  /**
   * Devuelve cuántas unidades de la divisa equivalen a 1 EUR. Ejemplo: getRate("USD") = 1.08 → 1
   * EUR = 1.08 USD
   */
  public double getRate(String currency) {
    if ("EUR".equalsIgnoreCase(currency)) {
      return 1.0;
    }
    refreshIfStale();
    Double rate = cachedRates.get().get(currency.toUpperCase());
    if (rate == null) {
      throw new IllegalArgumentException("Divisa no soportada: " + currency);
    }
    return rate;
  }

  /**
   * Convierte un precio en divisa origen a EUR.
   */
  public double convertToEur(double price, String currency) {
    if ("EUR".equalsIgnoreCase(currency)) {
      return price;
    }

    // GBp / GBX → peniques, convertir a GBP primero
    if ("GBp".equalsIgnoreCase(currency) || "GBX".equalsIgnoreCase(currency)) {
      return (price / 100.0) / getRate("GBP");
    }

    return price / getRate(currency.toUpperCase());
  }

  private void refreshIfStale() {
    Instant last = lastFetch.get();
    if (Instant.now().getEpochSecond() - last.getEpochSecond() < CACHE_TTL_SECONDS
        && !cachedRates.get().isEmpty()) {
      return;
    }
    fetchRates();
  }

  private synchronized void fetchRates() {
    // Double-check dentro del synchronized
    if (Instant.now().getEpochSecond() - lastFetch.get().getEpochSecond() < CACHE_TTL_SECONDS
        && !cachedRates.get().isEmpty()) {
      return;
    }

    try {
      log.info("Obteniendo tipos de cambio desde {}", RATES_URL);
      String body = restClient.get()
          .uri(RATES_URL)
          .retrieve()
          .body(String.class);

      if (body == null || body.isBlank()) {
        log.error("Respuesta vacía de la API de tasas");
        return;
      }

      JsonNode response = objectMapper.readTree(body);

      if (!response.has("rates")) {
        log.error("Respuesta inválida de la API de tasas");
        return;
      }

      Map<String, Double> newRates = new ConcurrentHashMap<>();
      response.get("rates").fields().forEachRemaining(entry ->
          newRates.put(entry.getKey(), entry.getValue().asDouble())
      );

      cachedRates.set(newRates);
      lastFetch.set(Instant.now());
      log.info("Tasas actualizadas: {} divisas cargadas (USD={}, GBP={})",
          newRates.size(),
          newRates.getOrDefault("USD", 0.0),
          newRates.getOrDefault("GBP", 0.0));

    } catch (Exception e) {
      log.error("Error obteniendo tipos de cambio: {}", e.getMessage());
    }
  }
}
