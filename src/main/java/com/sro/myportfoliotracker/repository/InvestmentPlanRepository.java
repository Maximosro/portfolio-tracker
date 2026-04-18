package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.InvestmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InvestmentPlanRepository extends JpaRepository<InvestmentPlan, Long> {
}

