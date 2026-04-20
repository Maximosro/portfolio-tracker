package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.simulator.MortgageRequest;
import com.sro.myportfoliotracker.dto.simulator.MortgageResult;
import com.sro.myportfoliotracker.dto.simulator.ProjectionRequest;
import com.sro.myportfoliotracker.dto.simulator.ProjectionResult;
import com.sro.myportfoliotracker.service.simulator.MortgageSimulatorService;
import com.sro.myportfoliotracker.service.simulator.ProjectionSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/simulator")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SimulatorController {

    private final ProjectionSimulatorService projectionService;
    private final MortgageSimulatorService mortgageService;

    @PostMapping("/projection")
    public ResponseEntity<ProjectionResult> projectPortfolio(@RequestBody ProjectionRequest request) {
        return ResponseEntity.ok(projectionService.simulate(request));
    }

    @GetMapping("/projection")
    public ResponseEntity<ProjectionResult> projectPortfolioDefaults() {
        return ResponseEntity.ok(projectionService.simulate(ProjectionRequest.builder().build()));
    }

    @PostMapping("/mortgage")
    public ResponseEntity<MortgageResult> simulateMortgage(@RequestBody MortgageRequest request) {
        return ResponseEntity.ok(mortgageService.simulate(request));
    }
}
