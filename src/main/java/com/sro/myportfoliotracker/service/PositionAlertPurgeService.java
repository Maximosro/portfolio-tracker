package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.repository.PositionAlertRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionAlertPurgeService {

  private final PositionAlertRepository positionAlertRepository;

  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void scheduledPurge() {
    Instant threshold = Instant.now().minus(30, ChronoUnit.DAYS);
    positionAlertRepository.deleteByTriggeredAtBefore(threshold);
    log.debug("PositionAlert: purga de registros anteriores a 30 días completada");
  }
}
