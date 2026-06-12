package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.PeriodReturnsDto;
import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.service.PortfolioMetricsService;
import com.sro.myportfoliotracker.service.PortfolioSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MetricsController {

  private final PortfolioMetricsService metricsService;
  private final PortfolioSnapshotService snapshotService;

  @GetMapping
  public ResponseEntity<PortfolioMetricsDto> getMetrics() {
    return ResponseEntity.ok(metricsService.calculateMetrics());
  }

  @GetMapping("/returns")
  public ResponseEntity<PeriodReturnsDto> getReturns() {
    return ResponseEntity.ok(snapshotService.calculateReturns());
  }
}

