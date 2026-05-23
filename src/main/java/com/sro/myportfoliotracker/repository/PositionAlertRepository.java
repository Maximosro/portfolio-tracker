package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.PositionAlert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionAlertRepository extends JpaRepository<PositionAlert, Long> {

  List<PositionAlert> findByTriggeredAtAfterOrderByTriggeredAtAsc(Instant from);

  Optional<PositionAlert> findByTickerAndAlertTypeAndTriggeredAtAfter(String ticker,
      String alertType, Instant from);

  void deleteByTriggeredAtBefore(Instant before);
}
