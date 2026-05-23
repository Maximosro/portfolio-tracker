package com.sro.myportfoliotracker.model;

import com.sro.myportfoliotracker.config.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "watchlist")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String ticker;

  @Column(name = "yahoo_ticker", length = 20)
  private String yahooTicker;

  @Column(length = 150)
  private String name;

  @Column(name = "current_price")
  private Double currentPrice;

  @Column(name = "previous_price")
  private Double previousPrice;

  @Column(name = "change_pct_day")
  private Double changePctDay;

  @Column(name = "change_pct_week")
  private Double changePctWeek;

  @Column(name = "change_pct_month")
  private Double changePctMonth;

  @Column(length = 10)
  private String currency;

  @Column(name = "last_price_update")
  @Convert(converter = InstantStringConverter.class)
  private Instant lastPriceUpdate;

  @Column(name = "created_at")
  @Convert(converter = InstantStringConverter.class)
  private Instant createdAt;

  @Column
  private Long volume;

  @Column(name = "avg_volume")
  private Long avgVolume;

  @Column(length = 200)
  private String notes;
}

