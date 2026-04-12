package com.sro.myportfoliotracker.dto;

import java.util.Map;

public record PortfolioMetricsDto(
        Double portfolioXirr,
        Map<String, Double> positionXirr
) {
}

