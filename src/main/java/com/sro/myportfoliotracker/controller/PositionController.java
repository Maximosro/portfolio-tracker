package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/positions")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public ResponseEntity<List<Position>> getAll() {
        return ResponseEntity.ok(positionService.findAll());
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<Position> getByTicker(@PathVariable String ticker) {
        return positionService.findByTicker(ticker)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Position position) {
        try {
            Position created = positionService.create(position);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{ticker}")
    public ResponseEntity<?> update(@PathVariable String ticker, @RequestBody Position position) {
        try {
            Position updated = positionService.update(ticker, position);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{ticker}")
    public ResponseEntity<?> delete(@PathVariable String ticker) {
        try {
            positionService.delete(ticker);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

