package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.PositionDetail;
import com.sro.myportfoliotracker.service.PositionDetailService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PositionDetailController {

  private final PositionDetailService positionDetailService;

  @GetMapping("/api/position-details")
  public ResponseEntity<List<PositionDetail>> getAll() {
    return ResponseEntity.ok(positionDetailService.findAll());
  }

  @GetMapping("/api/positions/{ticker}/detail")
  public ResponseEntity<PositionDetail> get(@PathVariable String ticker) {
    return positionDetailService.findByTicker(ticker)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.ok(PositionDetail.builder()
            .ticker(ticker.toUpperCase())
            .riskRating("MEDIUM")
            .build()));
  }

  @PutMapping("/api/positions/{ticker}/detail")
  public ResponseEntity<?> save(@PathVariable String ticker, @RequestBody PositionDetail detail) {
    try {
      PositionDetail saved = positionDetailService.save(ticker, detail);
      return ResponseEntity.ok(saved);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", e.getMessage()));
    }
  }
}

