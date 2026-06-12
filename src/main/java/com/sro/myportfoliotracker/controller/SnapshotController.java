package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.PeriodHistoryDto;
import com.sro.myportfoliotracker.model.PortfolioSnapshot;
import com.sro.myportfoliotracker.service.PortfolioSnapshotService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snapshots")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SnapshotController {

  private final PortfolioSnapshotService snapshotService;

  @GetMapping
  public ResponseEntity<List<PortfolioSnapshot>> getSnapshots(
      @RequestParam(defaultValue = "3m") String range) {
    return ResponseEntity.ok(snapshotService.getSnapshots(range));
  }

  @GetMapping("/period-history")
  public ResponseEntity<PeriodHistoryDto> getPeriodHistory(
      @RequestParam(defaultValue = "week") String period) {
    return ResponseEntity.ok(snapshotService.getPeriodHistory(period));
  }
}

