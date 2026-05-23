package com.sro.myportfoliotracker.model;

import com.sro.myportfoliotracker.config.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "price_history", indexes = {
    @Index(name = "idx_ph_ticker", columnList = "ticker"),
    @Index(name = "idx_ph_ticker_timestamp", columnList = "ticker, timestamp"),
    @Index(name = "idx_ph_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String ticker;

  @Column(nullable = false)
  @Convert(converter = InstantStringConverter.class)
  private Instant timestamp;

  /**
   * Precio en la divisa original de Yahoo Finance.
   */
  @Column(name = "raw_price", nullable = false)
  private Double rawPrice;

  /**
   * Divisa original (USD, GBP, GBp, EUR, etc.)
   */
  @Column(nullable = false, length = 5)
  private String currency;

  /**
   * Precio convertido a EUR.
   */
  @Column(name = "price_eur", nullable = false)
  private Double priceEur;
}

