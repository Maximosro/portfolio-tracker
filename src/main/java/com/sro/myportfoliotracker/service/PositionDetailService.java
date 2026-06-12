package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.PositionDetail;
import com.sro.myportfoliotracker.repository.PositionDetailRepository;
import com.sro.myportfoliotracker.repository.PositionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PositionDetailService {

  private final PositionDetailRepository positionDetailRepository;
  private final PositionRepository positionRepository;

  public List<PositionDetail> findAll() {
    return positionDetailRepository.findAll();
  }

  public Optional<PositionDetail> findByTicker(String ticker) {
    return positionDetailRepository.findById(ticker.toUpperCase());
  }

  @Transactional
  public PositionDetail save(String ticker, PositionDetail detail) {
    String normalizedTicker = ticker.toUpperCase();

    if (!positionRepository.existsById(normalizedTicker)) {
      throw new IllegalArgumentException("No existe posición con ticker: " + normalizedTicker);
    }

    detail.setTicker(normalizedTicker);
    detail.setUpdatedAt(Instant.now());

    return positionDetailRepository.save(detail);
  }

  @Transactional
  public void deleteByTicker(String ticker) {
    positionDetailRepository.deleteById(ticker.toUpperCase());
  }
}

