package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.service.PortfolioMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MetricsController {

    private final PortfolioMetricsService metricsService;

    @GetMapping
    public ResponseEntity<PortfolioMetricsDto> getMetrics() {
        return ResponseEntity.ok(metricsService.calculateMetrics());
    }
}

