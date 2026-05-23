package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionRepository extends JpaRepository<Position, String> {

}

