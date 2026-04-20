package com.sro.myportfoliotracker.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompoundInterestRequest {

    /** Capital inicial (€) */
    @Builder.Default
    private Double initialCapital = 0.0;

    /** Aportación mensual base (€) */
    @Builder.Default
    private Double monthlyContribution = 500.0;

    /** Rentabilidad anual esperada (%) */
    @Builder.Default
    private Double annualReturnPct = 7.0;

    /** Años de inversión */
    @Builder.Default
    private Integer years = 20;

    /** Inflación anual (%) para mostrar valor real */
    @Builder.Default
    private Double inflationPct = 2.5;

    /** Comisión anual sobre el patrimonio (%) — TER del fondo, por ejemplo */
    @Builder.Default
    private Double annualFeePct = 0.0;

    /**
     * Cambios en la aportación mensual a lo largo del tiempo.
     * Ej: [{fromYear: 5, amount: 700}, {fromYear: 10, amount: 1000}]
     * Si es null o vacío, la aportación es constante.
     */
    private List<ContributionChange> contributionChanges;

    /**
     * Cambios en la rentabilidad a lo largo del tiempo.
     * Ej: [{fromYear: 10, returnPct: 5}]
     */
    private List<ReturnChange> returnChanges;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContributionChange {
        private int fromYear;
        private double amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReturnChange {
        private int fromYear;
        private double returnPct;
    }
}

