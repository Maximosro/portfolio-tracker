package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.MarketSchedule;
import com.sro.myportfoliotracker.repository.MarketScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketScheduleService {

    private final MarketScheduleRepository marketScheduleRepository;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Determina si el mercado del ticker dado está abierto en este momento.
     * - Extrae el sufijo del ticker (ej: ".DE" de "SXR8.DE")
     * - Busca la configuración en BD
     * - Si no hay registro o no está habilitado → siempre abierto (true)
     * - Si es fin de semana en la zona horaria del mercado → cerrado
     * - Si la hora local está fuera del rango open/close → cerrado
     */
    public boolean isMarketOpen(String yahooTicker) {
        if (yahooTicker == null || yahooTicker.isBlank()) {
            return true;
        }

        String suffix = extractSuffix(yahooTicker);
        Optional<MarketSchedule> opt = marketScheduleRepository.findByTickerSuffix(suffix);

        if (opt.isEmpty()) {
            // Sin configuración para este sufijo → consultar siempre
            return true;
        }

        MarketSchedule schedule = opt.get();
        if (!Boolean.TRUE.equals(schedule.getEnabled())) {
            return true;
        }

        try {
            ZoneId zone = ZoneId.of(schedule.getTimezone());
            ZonedDateTime now = ZonedDateTime.now(zone);

            // Fin de semana → cerrado
            DayOfWeek day = now.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                log.debug("Mercado {} cerrado (fin de semana)", schedule.getMarketName());
                return false;
            }

            LocalTime openTime = LocalTime.parse(schedule.getOpenTime(), TIME_FMT);
            LocalTime closeTime = LocalTime.parse(schedule.getCloseTime(), TIME_FMT);
            LocalTime currentTime = now.toLocalTime();

            boolean open = !currentTime.isBefore(openTime) && !currentTime.isAfter(closeTime);
            if (!open) {
                log.debug("Mercado {} cerrado (hora local: {}, horario: {}-{})",
                        schedule.getMarketName(), currentTime, openTime, closeTime);
            }
            return open;

        } catch (Exception e) {
            log.warn("Error evaluando horario de mercado para {}: {}", yahooTicker, e.getMessage());
            return true; // En caso de error, consultar igualmente
        }
    }

    /**
     * Extrae el sufijo del ticker Yahoo.
     * "SXR8.DE" → ".DE", "AAPL" → null, "VUAA.L" → ".L"
     */
    public String extractSuffix(String yahooTicker) {
        if (yahooTicker == null) return null;
        int dotIndex = yahooTicker.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < yahooTicker.length() - 1) {
            return yahooTicker.substring(dotIndex).toUpperCase();
        }
        return null; // Sin sufijo = US market
    }

    /**
     * Devuelve todos los horarios configurados.
     */
    public List<MarketSchedule> findAll() {
        return marketScheduleRepository.findAll();
    }

    /**
     * Guarda o actualiza un horario de mercado.
     */
    public MarketSchedule save(MarketSchedule schedule) {
        return marketScheduleRepository.save(schedule);
    }

    /**
     * Elimina un horario por ID.
     */
    public void deleteById(Long id) {
        marketScheduleRepository.deleteById(id);
    }
}

