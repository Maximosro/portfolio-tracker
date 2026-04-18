package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.PlannedCashFlow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlannedCashFlowRepository extends JpaRepository<PlannedCashFlow, Long> {

    List<PlannedCashFlow> findByExecutedFalseOrderByExpectedDateAsc();

    List<PlannedCashFlow> findAllByOrderByExpectedDateAsc();
}

