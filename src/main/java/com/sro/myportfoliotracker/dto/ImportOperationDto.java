package com.sro.myportfoliotracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportOperationDto {

  private String ticker;
  private String name;
  private String yahooTicker;
  private String sector;
  private String color;
  private String date;   // formato YYYY-MM-DD
  private Double shares;
  private Double price;
  private String type;   // BUY o SELL
}

