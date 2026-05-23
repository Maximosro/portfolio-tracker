package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.PriceHistory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

  List<PriceHistory> findByTickerOrderByTimestampDesc(String ticker);

  List<PriceHistory> findByTickerAndTimestampBetweenOrderByTimestampAsc(String ticker, Instant from,
      Instant to);

  List<PriceHistory> findByTimestampBetweenOrderByTimestampAsc(Instant from, Instant to);

  List<PriceHistory> findByTickerAndTimestampAfterOrderByTimestampAsc(String ticker, Instant after);

  List<PriceHistory> findByTimestampAfterOrderByTimestampAsc(Instant after);

  void deleteAllByTicker(String ticker);

  void deleteAllByIdIn(List<Long> ids);

  long countByTimestampBefore(Instant before);

  @Query("SELECT DISTINCT p.ticker FROM PriceHistory p")
  List<String> findDistinctTickers();
}

