package com.sro.myportfoliotracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

  @Id
  @Column(name = "setting_key", nullable = false, length = 100)
  private String key;

  @Column(name = "setting_value", length = 500)
  private String value;
}

