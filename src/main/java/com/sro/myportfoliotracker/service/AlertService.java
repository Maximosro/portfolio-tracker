package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PositionDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final PositionService positionService;
    private final PositionDetailService positionDetailService;

    /**
     * Revisa todas las posiciones con detalle operativo y genera alertas
     * comparando el precio actual con los límites configurados.
     */
    public List<AlertDto> checkAlerts() {
        List<Position> positions = positionService.findAll();
        List<PositionDetail> details = positionDetailService.findAll();

        Map<String, Position> posMap = positions.stream()
                .collect(Collectors.toMap(Position::getTicker, Function.identity()));

        List<AlertDto> alerts = new ArrayList<>();

        for (PositionDetail detail : details) {
            Position pos = posMap.get(detail.getTicker());
            if (pos == null || pos.getCurrentPrice() == null || pos.getCurrentPrice() <= 0) {
                continue;
            }
            // No generar alertas para posiciones cerradas (vendidas totalmente)
            if (pos.getShares() == null || pos.getShares() <= 0) {
                continue;
            }

            double price = pos.getCurrentPrice();
            String ticker = pos.getTicker();
            String name = pos.getName();
            String color = pos.getColor();

            // STOP-LOSS: alertar si el precio está en o por debajo del stop-loss
            if (detail.getStopLoss() != null && detail.getStopLoss() > 0) {
                double dist = ((price - detail.getStopLoss()) / detail.getStopLoss()) * 100;
                if (price <= detail.getStopLoss()) {
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("STOP_LOSS").severity("DANGER")
                            .message("Precio (" + fmt(price) + " €) ha alcanzado o roto el Stop-Loss (" + fmt(detail.getStopLoss()) + " €)")
                            .currentPrice(price).limitPrice(detail.getStopLoss())
                            .distancePct(round2(dist))
                            .build());
                } else if (dist < 5) {
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("STOP_LOSS").severity("WARNING")
                            .message("Precio a solo " + fmt(dist) + "% del Stop-Loss (" + fmt(detail.getStopLoss()) + " €)")
                            .currentPrice(price).limitPrice(detail.getStopLoss())
                            .distancePct(round2(dist))
                            .build());
                }
            }

            // TAKE-PROFIT: alertar si el precio está en o por encima del take-profit
            if (detail.getTakeProfit() != null && detail.getTakeProfit() > 0) {
                double dist = ((detail.getTakeProfit() - price) / price) * 100;
                if (price >= detail.getTakeProfit()) {
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("TAKE_PROFIT").severity("DANGER")
                            .message("¡Precio (" + fmt(price) + " €) ha alcanzado el Take-Profit (" + fmt(detail.getTakeProfit()) + " €)!")
                            .currentPrice(price).limitPrice(detail.getTakeProfit())
                            .distancePct(round2(-dist))
                            .build());
                } else if (dist < 5) {
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("TAKE_PROFIT").severity("INFO")
                            .message("Precio a solo " + fmt(dist) + "% del Take-Profit (" + fmt(detail.getTakeProfit()) + " €)")
                            .currentPrice(price).limitPrice(detail.getTakeProfit())
                            .distancePct(round2(dist))
                            .build());
                }
            }

            // TRAILING STOP: alertar si se ha calculado y el precio ha caído ese % desde el máximo
            // Como no tenemos precio máximo histórico, usamos el precio medio como referencia
            // y alertamos si el trailing % implica que ya estamos cerca del umbral
            if (detail.getTrailingStopPct() != null && detail.getTrailingStopPct() > 0 && pos.getAvgPrice() != null) {
                double trailingPrice = price * (1 - detail.getTrailingStopPct() / 100);
                double distFromAvg = ((price - pos.getAvgPrice()) / pos.getAvgPrice()) * 100;
                // Si la posición está en pérdidas y el trailing es estrecho, avisar
                if (distFromAvg < 0 && Math.abs(distFromAvg) >= detail.getTrailingStopPct()) {
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("TRAILING_STOP").severity("WARNING")
                            .message("La caída desde P.Medio (" + fmt(Math.abs(distFromAvg)) + "%) supera el trailing stop (" + fmt(detail.getTrailingStopPct()) + "%)")
                            .currentPrice(price).limitPrice(trailingPrice)
                            .distancePct(round2(distFromAvg))
                            .build());
                }
            }

            // DCA TARGET: alertar si el precio baja al precio objetivo de compra
            if (detail.getDcaTargetPrice() != null && detail.getDcaTargetPrice() > 0) {
                double dist = ((price - detail.getDcaTargetPrice()) / detail.getDcaTargetPrice()) * 100;
                if (price <= detail.getDcaTargetPrice()) {
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("DCA_TARGET").severity("INFO")
                            .message("¡Precio (" + fmt(price) + " €) ha llegado al objetivo DCA (" + fmt(detail.getDcaTargetPrice()) + " €)! Oportunidad de promediar.")
                            .currentPrice(price).limitPrice(detail.getDcaTargetPrice())
                            .distancePct(round2(dist))
                            .build());
                } else if (dist < 5) {
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("DCA_TARGET").severity("INFO")
                            .message("Precio a " + fmt(dist) + "% del objetivo DCA (" + fmt(detail.getDcaTargetPrice()) + " €)")
                            .currentPrice(price).limitPrice(detail.getDcaTargetPrice())
                            .distancePct(round2(dist))
                            .build());
                }
            }

            // ALERT ABOVE
            if (detail.getAlertPriceAbove() != null && detail.getAlertPriceAbove() > 0) {
                if (price >= detail.getAlertPriceAbove()) {
                    double dist = ((price - detail.getAlertPriceAbove()) / detail.getAlertPriceAbove()) * 100;
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("ALERT_ABOVE").severity("WARNING")
                            .message("Precio (" + fmt(price) + " €) ha superado la alerta superior (" + fmt(detail.getAlertPriceAbove()) + " €)")
                            .currentPrice(price).limitPrice(detail.getAlertPriceAbove())
                            .distancePct(round2(dist))
                            .build());
                }
            }

            // ALERT BELOW
            if (detail.getAlertPriceBelow() != null && detail.getAlertPriceBelow() > 0) {
                if (price <= detail.getAlertPriceBelow()) {
                    double dist = ((price - detail.getAlertPriceBelow()) / detail.getAlertPriceBelow()) * 100;
                    alerts.add(AlertDto.builder()
                            .ticker(ticker).name(name).color(color)
                            .type("ALERT_BELOW").severity("WARNING")
                            .message("Precio (" + fmt(price) + " €) ha caído por debajo de la alerta inferior (" + fmt(detail.getAlertPriceBelow()) + " €)")
                            .currentPrice(price).limitPrice(detail.getAlertPriceBelow())
                            .distancePct(round2(dist))
                            .build());
                }
            }

            // TARGET WEIGHT: no genera alertas, solo informativo en el detalle de posición
        }

        // Ordenar: DANGER primero, luego WARNING, luego INFO
        alerts.sort((a, b) -> severityOrder(a.getSeverity()) - severityOrder(b.getSeverity()));

        return alerts;
    }

    private static int severityOrder(String severity) {
        return switch (severity) {
            case "DANGER" -> 0;
            case "WARNING" -> 1;
            case "INFO" -> 2;
            default -> 3;
        };
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

