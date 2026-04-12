package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.PortfolioMetricsDto;
import com.sro.myportfoliotracker.model.DcaEntry;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.repository.DcaEntryRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import com.sro.myportfoliotracker.service.XirrCalculator.CashFlow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

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
        // Flujos globales para el XIRR de cartera
        List<CashFlow> portfolioFlows = new ArrayList<>();

        for (Position pos : positions) {
            List<DcaEntry> dcaEntries = dcaByTicker.getOrDefault(pos.getTicker(), Collections.emptyList());

            if (pos.getCurrentPrice() == null) {
                positionXirr.put(pos.getTicker(), null);
                continue;
            }

            // Flujos de la posición
            List<CashFlow> flows = new ArrayList<>();

            if (dcaEntries.isEmpty()) {
                // Sin DCA registrado: no se puede calcular XIRR fiable
                positionXirr.put(pos.getTicker(), null);

                // Aún así, incluir la compra inicial en los flujos globales
                // usando la inversión total como compra única sin fecha real → no incluir
                continue;
            }

            // Cada compra DCA es un flujo negativo
            for (DcaEntry dca : dcaEntries) {
                double cost = dca.getShares() * dca.getPrice();
                flows.add(new CashFlow(dca.getDate(), -cost));
            }

            // Valor actual como flujo positivo terminal
            double currentValue = pos.getShares() * pos.getCurrentPrice();
            flows.add(new CashFlow(today, currentValue));

            // Ordenar por fecha
            flows.sort(Comparator.comparing(CashFlow::date));

            // Calcular XIRR de la posición
            Double xirr = XirrCalculator.calculate(flows);
            positionXirr.put(pos.getTicker(), xirr);

            // Acumular para el cálculo global
            portfolioFlows.addAll(flows.subList(0, flows.size() - 1)); // Solo los negativos (compras)

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

        return new PortfolioMetricsDto(portfolioXirr, positionXirr);
    }
}

