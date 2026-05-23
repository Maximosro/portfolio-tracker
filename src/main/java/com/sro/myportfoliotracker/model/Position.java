package com.sro.myportfoliotracker.model;

import com.sro.myportfoliotracker.config.InstantStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "positions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Position {

  @Id
  @Column(nullable = false, length = 10)
  private String ticker;

  @Column(length = 100)
  private String name;

  @Column(name = "yahoo_ticker", length = 20)
  private String yahooTicker;

  @Column(nullable = false)
  private Double shares;

  @Column(name = "avg_price", nullable = false)
  private Double avgPrice;

  @Column(name = "current_price")
  private Double currentPrice;

  @Column(length = 10)
  private String color;

  @Column(name = "target_pct")
  @Builder.Default
  private Double targetPct = 0.0;

  @Column(length = 200)
  private String sector;

  @Column(name = "previous_close")
  private Double previousClose;

  @Column
  private Long volume;

  @Column(name = "avg_volume")
  private Long avgVolume;

  @Column(name = "last_price_update")
  @Convert(converter = InstantStringConverter.class)
  private Instant lastPriceUpdate;
}

