package com.sro.myportfoliotracker.dto;

import java.util.Map;

public record PeriodReturnsDto(
    Map<String, PeriodReturnEntry> periods
) {

  public record PeriodReturnEntry(
      Double returnPct,
      Double returnEur,
      String label
  ) {

  }
}

