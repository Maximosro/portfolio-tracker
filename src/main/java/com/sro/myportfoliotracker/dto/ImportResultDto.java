package com.sro.myportfoliotracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportResultDto {

    private int totalOperations;
    private int positionsCreated;
    private int operationsImported;
    private List<String> warnings;
    private List<String> errors;
}

