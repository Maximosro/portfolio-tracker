package com.sro.myportfoliotracker.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "position_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(name = "alert_type", nullable = false, length = 20)
    private String alertType;

    @Column(nullable = false, length = 10)
    private String severity;

    @Column(name = "limit_price")
    private Double limitPrice;

    @Column(name = "current_price")
    private Double currentPrice;

    @Column(length = 500)
    private String message;

    @Column(length = 100)
    private String name;

    @Column(length = 10)
    private String color;

    @Column(name = "distance_pct")
    private Double distancePct;

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "notified_telegram", nullable = false)
    @Builder.Default
    private Boolean notifiedTelegram = false;
}
