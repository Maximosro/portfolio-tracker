package com.sro.myportfoliotracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tabla singleton que almacena el plan de inversión mensual del usuario. Solo debe existir una fila
 * con id=1.
 */
@Entity
@Table(name = "investment_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentPlan {

  @Id
  @Builder.Default
  private Long id = 1L;

  /**
   * Presupuesto mensual destinado a inversión (€/mes).
   */
  @Column(name = "monthly_budget")
  private Double monthlyBudget;

  /**
   * Tipo de presupuesto: FIJO o VARIABLE.
   */
  @Column(name = "budget_type", length = 10)
  @Builder.Default
  private String budgetType = "FIJO";

  /**
   * Notas libres sobre la estrategia de aportación.
   */
  @Column(length = 1000)
  private String notes;

  @Column(name = "updated_at")
  private Instant updatedAt;
}

