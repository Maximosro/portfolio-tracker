package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.MarketSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketScheduleRepository extends JpaRepository<MarketSchedule, Long> {
    Optional<MarketSchedule> findByTickerSuffix(String tickerSuffix);
}

