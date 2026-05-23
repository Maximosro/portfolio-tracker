package com.sro.myportfoliotracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketSchedule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Sufijo del ticker Yahoo (ej: ".DE", ".L", ".MC"). NULL = mercado US (sin sufijo)
   */
  @Column(name = "ticker_suffix", length = 10, unique = true)
  private String tickerSuffix;

  /**
   * Nombre descriptivo del mercado
   */
  @Column(name = "market_name", length = 100)
  private String marketName;

  /**
   * Zona horaria IANA (ej: "Europe/Berlin")
   */
  @Column(name = "timezone", nullable = false, length = 50)
  private String timezone;

  /**
   * Hora de apertura en formato HH:mm
   */
  @Column(name = "open_time", nullable = false, length = 5)
  private String openTime;

  /**
   * Hora de cierre en formato HH:mm
   */
  @Column(name = "close_time", nullable = false, length = 5)
  private String closeTime;

  /**
   * Si true, se respeta el horario. Si false, se consulta siempre (24/7)
   */
  @Column(name = "enabled", nullable = false)
  @Builder.Default
  private Boolean enabled = true;
}

