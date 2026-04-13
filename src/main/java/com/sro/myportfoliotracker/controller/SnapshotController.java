package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.PeriodHistoryDto;
import com.sro.myportfoliotracker.model.PortfolioSnapshot;
import com.sro.myportfoliotracker.service.PortfolioSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

