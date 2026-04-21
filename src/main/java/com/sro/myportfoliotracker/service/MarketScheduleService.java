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
     * Minutos de gracia tras el cierre para hacer una última actualización
     * y capturar el precio de cierre real.
     */
    private static final int POST_CLOSE_GRACE_MINUTES = 10;

    /**
     * Determina si el mercado del ticker dado está abierto en este momento,
     * o si está dentro de la ventana de gracia post-cierre (10 min).
     * - Extrae el sufijo del ticker (ej: ".DE" de "SXR8.DE")
     * - Busca la configuración en BD
     * - Si no hay registro o no está habilitado → siempre abierto (true)
     * - Si es fin de semana en la zona horaria del mercado → cerrado
     * - Si la hora local está dentro del horario o en la ventana de gracia → abierto
     */
    public boolean isMarketOpen(String yahooTicker) {
        if (yahooTicker == null || yahooTicker.isBlank()) {
            return true;
        }

        String suffix = extractSuffix(yahooTicker);
        Optional<MarketSchedule> opt = marketScheduleRepository.findByTickerSuffix(suffix);

        if (opt.isEmpty()) {
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
            LocalTime closeWithGrace = closeTime.plusMinutes(POST_CLOSE_GRACE_MINUTES);
            LocalTime currentTime = now.toLocalTime();

            boolean open = !currentTime.isBefore(openTime) && !currentTime.isAfter(closeWithGrace);
            if (!open) {
                log.debug("Mercado {} cerrado (hora local: {}, horario: {}-{}, gracia hasta {})",
                        schedule.getMarketName(), currentTime, openTime, closeTime, closeWithGrace);
            } else if (currentTime.isAfter(closeTime)) {
                log.debug("Mercado {} en ventana post-cierre (hora local: {}, cierre: {}, gracia hasta {})",
                        schedule.getMarketName(), currentTime, closeTime, closeWithGrace);
            }
            return open;

        } catch (Exception e) {
            log.warn("Error evaluando horario de mercado para {}: {}", yahooTicker, e.getMessage());
            return true;
        }
    }

    /**
     * Indica si un ticker está actualmente en la ventana post-cierre
     * (entre closeTime y closeTime + gracia).
     */
    public boolean isInPostCloseGrace(String yahooTicker) {
        if (yahooTicker == null || yahooTicker.isBlank()) return false;
        String suffix = extractSuffix(yahooTicker);
        Optional<MarketSchedule> opt = marketScheduleRepository.findByTickerSuffix(suffix);
        if (opt.isEmpty()) return false;
        MarketSchedule schedule = opt.get();
        if (!Boolean.TRUE.equals(schedule.getEnabled())) return false;
        try {
            ZoneId zone = ZoneId.of(schedule.getTimezone());
            ZonedDateTime now = ZonedDateTime.now(zone);
            DayOfWeek day = now.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
            LocalTime closeTime = LocalTime.parse(schedule.getCloseTime(), TIME_FMT);
            LocalTime currentTime = now.toLocalTime();
            return currentTime.isAfter(closeTime) && !currentTime.isAfter(closeTime.plusMinutes(POST_CLOSE_GRACE_MINUTES));
        } catch (Exception e) {
            return false;
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
     * Comprueba si existe un horario configurado para el sufijo dado.
     */
    public boolean hasScheduleFor(String suffix) {
        return marketScheduleRepository.findByTickerSuffix(suffix).isPresent();
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

