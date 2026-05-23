package com.sro.myportfoliotracker.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

