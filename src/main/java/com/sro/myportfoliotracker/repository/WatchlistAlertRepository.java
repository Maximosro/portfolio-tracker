package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.WatchlistAlert;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistAlertRepository extends JpaRepository<WatchlistAlert, Long> {

  List<WatchlistAlert> findByWatchlistItemId(Long watchlistItemId);

  List<WatchlistAlert> findByEnabledTrue();

  void deleteByWatchlistItemId(Long watchlistItemId);
}

