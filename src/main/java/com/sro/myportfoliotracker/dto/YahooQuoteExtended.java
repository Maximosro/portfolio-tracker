package com.sro.myportfoliotracker.dto;

/**
 * Cotización extendida de Yahoo Finance con variaciones temporales.
 */
public record YahooQuoteExtended(
        double price,
        String currency,
        Double changePctDay,
        Double changePctWeek,
        Double changePctMonth
) {}

