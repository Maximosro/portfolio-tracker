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
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM positions", Integer.class);
        if (count != null && count > 0) {
            log.info("Base de datos ya contiene datos, omitiendo seed");
            return;
        }

        log.info("Base de datos vacía, ejecutando seed...");
        executeSqlFile("seed.sql", "./data/seed.sql");
        executeSqlFile("price_history_seed.sql", "./data/price_history_seed.sql");
        log.info("Seed completado");
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

