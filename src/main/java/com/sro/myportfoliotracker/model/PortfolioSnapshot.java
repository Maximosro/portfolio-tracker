package com.sro.myportfoliotracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

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

