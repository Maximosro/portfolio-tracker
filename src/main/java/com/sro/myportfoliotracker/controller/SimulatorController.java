package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.simulator.CompoundInterestRequest;
import com.sro.myportfoliotracker.dto.simulator.CompoundInterestResult;
import com.sro.myportfoliotracker.dto.simulator.MonteCarloRequest;
import com.sro.myportfoliotracker.dto.simulator.MonteCarloResult;
import com.sro.myportfoliotracker.dto.simulator.MortgageRequest;
import com.sro.myportfoliotracker.dto.simulator.MortgageResult;
import com.sro.myportfoliotracker.dto.simulator.ProjectionRequest;
import com.sro.myportfoliotracker.dto.simulator.ProjectionResult;
import com.sro.myportfoliotracker.dto.simulator.WithdrawalRequest;
import com.sro.myportfoliotracker.dto.simulator.WithdrawalResult;
import com.sro.myportfoliotracker.service.simulator.CompoundInterestService;
import com.sro.myportfoliotracker.service.simulator.MonteCarloService;
import com.sro.myportfoliotracker.service.simulator.MortgageSimulatorService;
import com.sro.myportfoliotracker.service.simulator.ProjectionSimulatorService;
import com.sro.myportfoliotracker.service.simulator.WithdrawalSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SimulatorController {

  private final ProjectionSimulatorService projectionService;
  private final MortgageSimulatorService mortgageService;
  private final WithdrawalSimulatorService withdrawalService;
  private final CompoundInterestService compoundInterestService;
  private final MonteCarloService monteCarloService;

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

  @PostMapping("/withdrawal")
  public ResponseEntity<WithdrawalResult> simulateWithdrawal(
      @RequestBody WithdrawalRequest request) {
    return ResponseEntity.ok(withdrawalService.simulate(request));
  }

  @PostMapping("/compound")
  public ResponseEntity<CompoundInterestResult> simulateCompound(
      @RequestBody CompoundInterestRequest request) {
    return ResponseEntity.ok(compoundInterestService.simulate(request));
  }

  @PostMapping("/montecarlo")
  public ResponseEntity<MonteCarloResult> simulateMonteCarlo(
      @RequestBody MonteCarloRequest request) {
    return ResponseEntity.ok(monteCarloService.simulate(request));
  }
}
