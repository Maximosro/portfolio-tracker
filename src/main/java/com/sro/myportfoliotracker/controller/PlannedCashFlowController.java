package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.PlannedCashFlow;
import com.sro.myportfoliotracker.repository.PlannedCashFlowRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planned-cashflows")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class PlannedCashFlowController {

  private final PlannedCashFlowRepository repository;

  @GetMapping
  public ResponseEntity<List<PlannedCashFlow>> getAll() {
    return ResponseEntity.ok(repository.findAllByOrderByExpectedDateAsc());
  }

  @GetMapping("/pending")
  public ResponseEntity<List<PlannedCashFlow>> getPending() {
    return ResponseEntity.ok(repository.findByExecutedFalseOrderByExpectedDateAsc());
  }

  @PostMapping
  public ResponseEntity<PlannedCashFlow> create(@RequestBody PlannedCashFlow cashFlow) {
    if (cashFlow.getExecuted() == null) {
      cashFlow.setExecuted(false);
    }
    return ResponseEntity.ok(repository.save(cashFlow));
  }

  @PutMapping("/{id}")
  public ResponseEntity<PlannedCashFlow> update(@PathVariable Long id,
      @RequestBody PlannedCashFlow cashFlow) {
    PlannedCashFlow existing = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("No existe flujo con id: " + id));
    existing.setDescription(cashFlow.getDescription());
    existing.setAmount(cashFlow.getAmount());
    existing.setExpectedDate(cashFlow.getExpectedDate());
    existing.setType(cashFlow.getType());
    existing.setExecuted(cashFlow.getExecuted());
    return ResponseEntity.ok(repository.save(existing));
  }

  @PatchMapping("/{id}/execute")
  public ResponseEntity<PlannedCashFlow> markExecuted(@PathVariable Long id) {
    PlannedCashFlow existing = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("No existe flujo con id: " + id));
    existing.setExecuted(true);
    return ResponseEntity.ok(repository.save(existing));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    repository.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}

