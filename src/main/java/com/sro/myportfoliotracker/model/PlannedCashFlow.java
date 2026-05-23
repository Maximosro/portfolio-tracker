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

/**
 * Flujo de caja futuro planificado (aportación extraordinaria o recurrente).
 */
@Entity
@Table(name = "planned_cash_flows")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedCashFlow {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Descripción del flujo (ej: "Bonus anual", "Ahorro extra verano").
   */
  @Column(nullable = false, length = 200)
  private String description;

  /**
   * Importe previsto en euros.
   */
  @Column(nullable = false)
  private Double amount;

  /**
   * Fecha esperada del flujo.
   */
  @Column(name = "expected_date", nullable = false)
  @Convert(converter = LocalDateStringConverter.class)
  private LocalDate expectedDate;

  /**
   * Tipo de flujo: EXTRAORDINARIA o RECURRENTE.
   */
  @Column(length = 15)
  @Builder.Default
  private String type = "EXTRAORDINARIA";

  /**
   * Si ya se ha ejecutado/materializado.
   */
  @Column
  @Builder.Default
  private Boolean executed = false;
}

