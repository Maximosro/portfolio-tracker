package com.sro.myportfoliotracker.dto;

import java.util.List;

public record PeriodHistoryDto(
        String period,
        String label,
        List<HistoryEntry> entries,
        Double totalReturnPct,
        Double totalReturnEur
) {
    public record HistoryEntry(
            String date,
            String groupLabel,
            Double totalValue,
            Double totalInvested,
            Double returnPct,
            Double returnEur
    ) {}
}

