package com.sro.myportfoliotracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

