package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PositionAlert;
import com.sro.myportfoliotracker.model.PositionDetail;
import com.sro.myportfoliotracker.repository.PositionAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");

    private final PositionService positionService;
    private final PositionDetailService positionDetailService;
    private final PositionAlertRepository positionAlertRepository;

    /**
     * Devuelve las alertas de hoy combinando los registros persistidos
     * con las condiciones actuales. Cada par (ticker, alertType) solo
     * genera una alerta por día natural.
     */
    public List<AlertDto> getTodayAlerts() {
        Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();

        Map<String, PositionAlert> todayAlerts = positionAlertRepository
                .findByTriggeredAtAfterOrderByTriggeredAtAsc(startOfToday)
                .stream()
                .collect(Collectors.toMap(
                        pa -> pa.getTicker() + "|" + pa.getAlertType(),
                        Function.identity(),
                        (a, b) -> a));

        List<Position> positions = positionService.findAll();
        List<PositionDetail> details = positionDetailService.findAll();

        Map<String, Position> posMap = positions.stream()
                .collect(Collectors.toMap(Position::getTicker, Function.identity()));

        List<AlertDto> currentAlerts = evaluateConditions(posMap, details);

        for (AlertDto alert : currentAlerts) {
            String key = alert.getTicker() + "|" + alert.getType();
            if (!todayAlerts.containsKey(key)) {
                PositionAlert pa = PositionAlert.builder()
                        .ticker(alert.getTicker())
                        .alertType(alert.getType())
                        .severity(alert.getSeverity())
                        .limitPrice(alert.getLimitPrice())
                        .currentPrice(alert.getCurrentPrice())
                        .message(alert.getMessage())
                        .name(alert.getName())
                        .color(alert.getColor())
                        .distancePct(alert.getDistancePct())
                        .triggeredAt(Instant.now())
                        .notifiedTelegram(false)
                        .build();
                pa = positionAlertRepository.save(pa);
                todayAlerts.put(key, pa);
            }
        }

        return todayAlerts.values().stream()
                .map(this::toAlertDto)
                .sorted(Comparator.comparingInt(a -> severityOrder(a.getSeverity())))
                .collect(Collectors.toList());
    }

    /**
     * @deprecated Usar {@link #getTodayAlerts()} que añade cooldown diario.
     * Mantenido por compatibilidad con ExportService.
     */
    @Deprecated
    public List<AlertDto> checkAlerts() {
        return getTodayAlerts();
    }

    /**
     * Evalúa todas las condiciones actuales y devuelve AlertDtos sin persistencia.
     */
    private List<AlertDto> evaluateConditions(Map<String, Position> posMap, List<PositionDetail> details) {
        List<AlertDto> alerts = new ArrayList<>();

        for (PositionDetail detail : details) {
            Position pos = posMap.get(detail.getTicker());
            if (pos == null || pos.getCurrentPrice() == null || pos.getCurrentPrice() <= 0) {
                continue;
            }
            if (pos.getShares() == null || pos.getShares() <= 0) {
                continue;
            }

            double price = pos.getCurrentPrice();
            String ticker = pos.getTicker();
            String name = pos.getName();
            String color = pos.getColor();

            // STOP-LOSS
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

            // TAKE-PROFIT
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

            // TRAILING STOP
            if (detail.getTrailingStopPct() != null && detail.getTrailingStopPct() > 0 && pos.getAvgPrice() != null) {
                double trailingPrice = price * (1 - detail.getTrailingStopPct() / 100);
                double distFromAvg = ((price - pos.getAvgPrice()) / pos.getAvgPrice()) * 100;
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

            // DCA TARGET
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
        }

        alerts.sort((a, b) -> severityOrder(a.getSeverity()) - severityOrder(b.getSeverity()));
        return alerts;
    }

    private AlertDto toAlertDto(PositionAlert pa) {
        return AlertDto.builder()
                .ticker(pa.getTicker())
                .name(pa.getName())
                .color(pa.getColor())
                .type(pa.getAlertType())
                .severity(pa.getSeverity())
                .message(pa.getMessage())
                .currentPrice(pa.getCurrentPrice())
                .limitPrice(pa.getLimitPrice())
                .distancePct(pa.getDistancePct())
                .alertId(pa.getId())
                .triggeredAt(pa.getTriggeredAt())
                .build();
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
