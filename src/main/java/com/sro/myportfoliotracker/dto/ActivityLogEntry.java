package com.sro.myportfoliotracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLogEntry {

    private Instant timestamp;
    private String category;   // PRICE, TELEGRAM, WATCHLIST, ALERT, DCA, SNAPSHOT, SYSTEM
    private String level;      // INFO, SUCCESS, WARNING, ERROR
    private String message;
    private String ticker;     // optional, related ticker
    private String icon;       // emoji icon for the entry
}

