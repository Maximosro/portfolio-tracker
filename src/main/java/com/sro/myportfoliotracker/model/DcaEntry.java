package com.sro.myportfoliotracker.model;

import com.sro.myportfoliotracker.config.LocalDateStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dca_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DcaEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 10)
  private String ticker;

  @Column(nullable = false)
  @Convert(converter = LocalDateStringConverter.class)
  private LocalDate date;

  @Column(nullable = false)
  private Double shares;

  @Column(nullable = false)
  private Double price;

  /**
   * Tipo de operación: BUY (compra) o SELL (venta).
   */
  @Column(name = "entry_type", length = 4)
  @Builder.Default
  private String type = "BUY";
}

