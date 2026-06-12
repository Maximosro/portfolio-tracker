package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.MarketSchedule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketScheduleRepository extends JpaRepository<MarketSchedule, Long> {

  Optional<MarketSchedule> findByTickerSuffix(String tickerSuffix);
}

