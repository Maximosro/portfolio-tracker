package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.WatchlistAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchlistAlertRepository extends JpaRepository<WatchlistAlert, Long> {

    List<WatchlistAlert> findByWatchlistItemId(Long watchlistItemId);

    List<WatchlistAlert> findByEnabledTrue();

    void deleteByWatchlistItemId(Long watchlistItemId);
}

