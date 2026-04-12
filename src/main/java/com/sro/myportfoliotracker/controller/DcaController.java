package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.service.DcaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dca")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DcaController {

    private final DcaService dcaService;

    @GetMapping
    public ResponseEntity<List<DcaEntry>> getAll() {
        return ResponseEntity.ok(dcaService.findAll());
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<List<DcaEntry>> getByTicker(@PathVariable String ticker) {
        return ResponseEntity.ok(dcaService.findByTicker(ticker));
    }

    @PostMapping
    public ResponseEntity<?> addEntry(@RequestBody DcaEntry entry) {
        try {
            DcaEntry saved = dcaService.addEntry(entry);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEntry(@PathVariable Long id, @RequestBody DcaEntry entry) {
        try {
            DcaEntry updated = dcaService.updateEntry(id, entry);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEntry(@PathVariable Long id) {
        try {
            dcaService.deleteEntry(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

