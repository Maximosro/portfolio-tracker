package com.sro.myportfoliotracker.model;

import com.sro.myportfoliotracker.config.InstantStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "watchlist_alert")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistAlert {

    public enum AlertType {
        PRICE_ABOVE,
        PRICE_BELOW,
        VOLUME_ABOVE,
        VOLUME_BELOW
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watchlist_item_id", nullable = false)
    private Long watchlistItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 20)
    private AlertType alertType;

    /** Para PRICE_ABOVE/BELOW: precio en EUR. Para VOLUME_ABOVE/BELOW: ratio (ej. 2.0 = 2x avg) */
    @Column(nullable = false)
    private Double threshold;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean triggered = false;

    @Column(name = "last_triggered_at")
    @Convert(converter = InstantStringConverter.class)
    private Instant lastTriggeredAt;

    @Column(name = "created_at")
    @Convert(converter = InstantStringConverter.class)
    private Instant createdAt;
}

