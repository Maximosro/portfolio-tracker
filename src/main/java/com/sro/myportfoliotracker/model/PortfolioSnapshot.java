package com.sro.myportfoliotracker.model;

import com.sro.myportfoliotracker.config.LocalDateStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "portfolio_snapshots", indexes = {
    @Index(name = "idx_ps_date", columnList = "date", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSnapshot {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  @Convert(converter = LocalDateStringConverter.class)
  private LocalDate date;

  /**
   * Valor de mercado total: Σ(shares × currentPrice)
   */
  @Column(name = "total_value", nullable = false)
  private Double totalValue;

  /**
   * Capital invertido total: Σ(shares × avgPrice)
   */
  @Column(name = "total_invested", nullable = false)
  private Double totalInvested;
}

