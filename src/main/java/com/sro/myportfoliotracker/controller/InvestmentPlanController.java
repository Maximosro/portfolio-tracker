package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.InvestmentPlan;
import com.sro.myportfoliotracker.repository.InvestmentPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/investment-plan")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class InvestmentPlanController {

    private final InvestmentPlanRepository repository;

    /**
     * Obtiene el plan de inversión actual (singleton, id=1).
     */
    @GetMapping
    public ResponseEntity<InvestmentPlan> get() {
        InvestmentPlan plan = repository.findById(1L)
                .orElse(InvestmentPlan.builder().id(1L).build());
        return ResponseEntity.ok(plan);
    }

    /**
     * Crea o actualiza el plan de inversión.
     */
    @PutMapping
    public ResponseEntity<InvestmentPlan> save(@RequestBody InvestmentPlan plan) {
        plan.setId(1L); // Siempre singleton
        plan.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(repository.save(plan));
    }
}

