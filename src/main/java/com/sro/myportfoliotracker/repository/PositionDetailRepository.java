package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.PositionDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionDetailRepository extends JpaRepository<PositionDetail, String> {

}

