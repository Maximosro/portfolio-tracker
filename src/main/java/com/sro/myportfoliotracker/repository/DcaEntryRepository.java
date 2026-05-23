package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.DcaEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DcaEntryRepository extends JpaRepository<DcaEntry, Long> {

  List<DcaEntry> findByTickerOrderByDateDesc(String ticker);

  List<DcaEntry> findByTickerOrderByDateAsc(String ticker);

  List<DcaEntry> findAllByOrderByDateDesc();

  void deleteAllByTicker(String ticker);

  List<DcaEntry> findByDateGreaterThanEqual(LocalDate from);
}

