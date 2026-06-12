package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.PlannedCashFlow;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlannedCashFlowRepository extends JpaRepository<PlannedCashFlow, Long> {

  List<PlannedCashFlow> findByExecutedFalseOrderByExpectedDateAsc();

  List<PlannedCashFlow> findAllByOrderByExpectedDateAsc();
}

