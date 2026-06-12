package com.sro.myportfoliotracker.dto;

import java.time.Instant;
import java.util.List;

public record PriceUpdateResult(
    int updated,
    int errors,
    Instant timestamp,
    List<String> failedTickers
) {

}

