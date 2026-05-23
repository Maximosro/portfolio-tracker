package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.PortfolioSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {

  Optional<PortfolioSnapshot> findByDate(LocalDate date);

  List<PortfolioSnapshot> findByDateGreaterThanEqualOrderByDateAsc(LocalDate from);

  List<PortfolioSnapshot> findAllByOrderByDateAsc();

  Optional<PortfolioSnapshot> findFirstByDateLessThanEqualOrderByDateDesc(LocalDate date);

  List<PortfolioSnapshot> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
