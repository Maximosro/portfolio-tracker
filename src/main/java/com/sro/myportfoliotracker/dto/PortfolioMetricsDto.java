package com.sro.myportfoliotracker.dto;

import java.util.Map;

public record PortfolioMetricsDto(
    Double portfolioXirr,
    Map<String, Double> positionXirr,
    Map<String, Double> positionRealizedPL,
    Double totalRealizedPL
) {

}

