package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.service.XirrCalculator.CashFlow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioMetricsService {

  private final PositionRepository positionRepository;
  private final DcaEntryRepository dcaEntryRepository;

  public PortfolioMetricsDto calculateMetrics() {
    List<Position> positions = positionRepository.findAll();
    List<DcaEntry> allDca = dcaEntryRepository.findAllByOrderByDateDesc();
    LocalDate today = LocalDate.now();

    // DCA agrupado por ticker
    Map<String, List<DcaEntry>> dcaByTicker = allDca.stream()
        .collect(Collectors.groupingBy(DcaEntry::getTicker));

    // TIR por posición
    Map<String, Double> positionXirr = new LinkedHashMap<>();
    // P&L realizado por posición (de las ventas)
    Map<String, Double> positionRealizedPL = new LinkedHashMap<>();
    double totalRealizedPL = 0.0;
    // Flujos globales para el XIRR de cartera
    List<CashFlow> portfolioFlows = new ArrayList<>();

    for (Position pos : positions) {
      List<DcaEntry> dcaEntries = dcaByTicker.getOrDefault(pos.getTicker(),
          Collections.emptyList());

      // Calcular P&L realizado de las ventas usando el avgPrice de la posición
      double realizedPL = 0.0;
      if (!dcaEntries.isEmpty() && pos.getAvgPrice() != null && pos.getAvgPrice() > 0) {
        for (DcaEntry dca : dcaEntries) {
          if ("SELL".equals(dca.getType())) {
            realizedPL += dca.getShares() * (dca.getPrice() - pos.getAvgPrice());
          }
        }
      }
      positionRealizedPL.put(pos.getTicker(), Math.round(realizedPL * 100.0) / 100.0);
      totalRealizedPL += realizedPL;

      if (pos.getCurrentPrice() == null) {
        positionXirr.put(pos.getTicker(), null);
        continue;
      }

      // Flujos de la posición
      List<CashFlow> flows = new ArrayList<>();

      if (dcaEntries.isEmpty()) {
        // Sin DCA registrado: no se puede calcular XIRR fiable
        positionXirr.put(pos.getTicker(), null);
        continue;
      }

      // Cada compra DCA es un flujo negativo, cada venta es un flujo positivo
      for (DcaEntry dca : dcaEntries) {
        double amount = dca.getShares() * dca.getPrice();
        if ("SELL".equals(dca.getType())) {
          flows.add(new CashFlow(dca.getDate(), amount));
        } else {
          flows.add(new CashFlow(dca.getDate(), -amount));
        }
      }

      // Valor actual como flujo positivo terminal (solo si quedan acciones)
      double currentValue = pos.getShares() * pos.getCurrentPrice();
      if (currentValue > 0 || pos.getShares() > 0) {
        flows.add(new CashFlow(today, currentValue));
      }

      // Ordenar por fecha
      flows.sort(Comparator.comparing(CashFlow::date));

      // Calcular XIRR de la posición
      Double xirr = XirrCalculator.calculate(flows);
      positionXirr.put(pos.getTicker(), xirr);

      // Acumular para el cálculo global
      portfolioFlows.addAll(flows.subList(0, flows.size() - 1));

      log.debug("XIRR {} → {} flujos → {}", pos.getTicker(), flows.size(),
          xirr != null ? String.format("%.2f%%", xirr * 100) : "N/A");
    }

    // Flujo terminal global: valor total actual de la cartera
    double totalValue = positions.stream()
        .filter(p -> p.getCurrentPrice() != null)
        .mapToDouble(p -> p.getShares() * p.getCurrentPrice())
        .sum();
    portfolioFlows.add(new CashFlow(today, totalValue));
    portfolioFlows.sort(Comparator.comparing(CashFlow::date));

    Double portfolioXirr = XirrCalculator.calculate(portfolioFlows);

    return new PortfolioMetricsDto(
        portfolioXirr,
        positionXirr,
        positionRealizedPL,
        Math.round(totalRealizedPL * 100.0) / 100.0
    );
  }
}

