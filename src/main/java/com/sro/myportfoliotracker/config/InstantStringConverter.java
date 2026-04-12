package com.sro.myportfoliotracker.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Convierte Instant ↔ String para SQLite.
 * Almacena en formato "yyyy-MM-dd HH:mm:ss.SSSSSS" (compatible con SQLite JDBC).
 * Al leer, acepta tanto el formato con espacio como ISO-8601 (con T y/o Z).
 */
@Converter
public class InstantStringConverter implements AttributeConverter<Instant, String> {

    private static final DateTimeFormatter WRITE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Override
    public String convertToDatabaseColumn(Instant instant) {
        if (instant == null) return null;
        return WRITE_FMT.format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    @Override
    public Instant convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return null;
        // Normalizar a ISO-8601 para que Instant.parse() lo entienda
        String normalized = value.trim().replace(" ", "T");
        if (!normalized.endsWith("Z")) {
            normalized += "Z";
        }
        return Instant.parse(normalized);
    }
}

