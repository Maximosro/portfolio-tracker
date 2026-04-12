package com.sro.myportfoliotracker.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Convierte LocalDate ↔ String para SQLite.
 * Almacena en formato "yyyy-MM-dd".
 */
@Converter
public class LocalDateStringConverter implements AttributeConverter<LocalDate, String> {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    @Override
    public String convertToDatabaseColumn(LocalDate date) {
        if (date == null) return null;
        return date.format(FMT);
    }

    @Override
    public LocalDate convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return null;
        // Solo tomar la parte de fecha si viene con hora
        String dateOnly = value.trim();
        if (dateOnly.contains("T")) {
            dateOnly = dateOnly.substring(0, dateOnly.indexOf('T'));
        } else if (dateOnly.contains(" ")) {
            dateOnly = dateOnly.substring(0, dateOnly.indexOf(' '));
        }
        return LocalDate.parse(dateOnly, FMT);
    }
}

