package com.sro.myportfoliotracker.model;

import com.sro.myportfoliotracker.config.InstantStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "position_details")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PositionDetail {

    @Id
    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(length = 2000)
    private String notes;

    @Column(name = "stop_loss")
    private Double stopLoss;

    @Column(name = "take_profit")
    private Double takeProfit;

    @Column(name = "dca_target_price")
    private Double dcaTargetPrice;

    @Column(name = "trailing_stop_pct")
    private Double trailingStopPct;

    @Column(name = "alert_price_above")
    private Double alertPriceAbove;

    @Column(name = "alert_price_below")
    private Double alertPriceBelow;

    @Column(name = "risk_rating", length = 10)
    @Builder.Default
    private String riskRating = "MEDIUM";

    @Column(name = "strategy", length = 500)
    private String strategy;

    @Column(name = "target_weight_pct")
    private Double targetWeightPct;

    @Column(name = "updated_at")
    @Convert(converter = InstantStringConverter.class)
    private Instant updatedAt;
}

