package com.sro.myportfoliotracker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // Run before price update (which has default order)
    public void seed() {
        seedPositions();
        seedMarketSchedules();
    }

    private void seedPositions() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM positions", Integer.class);
        if (count != null && count > 0) {
            log.info("Base de datos ya contiene datos, omitiendo seed de posiciones");
            return;
        }

        log.info("Base de datos vacía, ejecutando seed...");
        executeSqlFile("seed.sql", "./data/seed.sql");
        executeSqlFile("price_history_seed.sql", "./data/price_history_seed.sql");
        log.info("Seed completado");
    }

    private void seedMarketSchedules() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM market_schedules", Integer.class);
            if (count != null && count > 0) {
                log.info("Horarios de mercado ya configurados ({} registros)", count);
                return;
            }
        } catch (Exception e) {
            log.debug("Tabla market_schedules aún no existe, se creará por Hibernate");
            return;
        }

        log.info("Insertando horarios de mercado por defecto...");
        String[] inserts = {
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES (NULL, 'US (NYSE/NASDAQ)', 'America/New_York', '09:30', '16:00', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.DE', 'Alemania (XETRA)', 'Europe/Berlin', '08:00', '17:30', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.F', 'Alemania (Frankfurt)', 'Europe/Berlin', '08:00', '20:00', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.L', 'Reino Unido (LSE)', 'Europe/London', '08:00', '16:30', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.MC', 'España (BME)', 'Europe/Madrid', '09:00', '17:30', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.PA', 'Francia (Euronext Paris)', 'Europe/Paris', '09:00', '17:30', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.MI', 'Italia (Borsa Italiana)', 'Europe/Rome', '09:00', '17:30', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.AS', 'Países Bajos (Euronext Amsterdam)', 'Europe/Amsterdam', '09:00', '17:30', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.TO', 'Canadá (TSX)', 'America/Toronto', '09:30', '16:00', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.HK', 'Hong Kong (HKEX)', 'Asia/Hong_Kong', '09:30', '16:00', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.T', 'Japón (TSE)', 'Asia/Tokyo', '09:00', '15:00', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.SW', 'Suiza (SIX)', 'Europe/Zurich', '09:00', '17:30', true) ON CONFLICT (ticker_suffix) DO NOTHING",
            "INSERT INTO market_schedules (ticker_suffix, market_name, timezone, open_time, close_time, enabled) VALUES ('.BR', 'Bélgica (Euronext Brussels)', 'Europe/Brussels', '09:00', '17:30', true) ON CONFLICT (ticker_suffix) DO NOTHING",
        };

        for (String sql : inserts) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.warn("Error insertando horario de mercado: {}", e.getMessage());
            }
        }
        log.info("Horarios de mercado por defecto insertados");
    }

    /**
     * Busca el fichero SQL primero en filesystem (./data/) y luego en classpath (dentro del JAR).
     */
    private void executeSqlFile(String classpathPath, String filesystemPath) {
        Resource resource = new FileSystemResource(filesystemPath);
        if (!resource.exists()) {
            resource = new ClassPathResource(classpathPath);
        }
        if (!resource.exists()) {
            log.warn("Fichero seed no encontrado ni en filesystem ({}) ni en classpath ({})", filesystemPath, classpathPath);
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) continue;
                sb.append(line);
                if (line.endsWith(";")) {
                    jdbcTemplate.execute(sb.toString());
                    sb.setLength(0);
                }
            }
            log.info("Ejecutado: {}", resource.getFilename());
        } catch (Exception e) {
            log.error("Error ejecutando {}: {}", resource.getFilename(), e.getMessage());
        }
    }
}

