package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.WatchlistItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

  Optional<WatchlistItem> findByTickerIgnoreCase(String ticker);

  boolean existsByTickerIgnoreCase(String ticker);
}

