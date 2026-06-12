package com.sro.myportfoliotracker.repository;

import com.sro.myportfoliotracker.model.TelegramChannelMessage;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramChannelMessageRepository extends
    JpaRepository<TelegramChannelMessage, Long> {

  List<TelegramChannelMessage> findTop100ByOrderByDateEpochDesc();

  void deleteByReceivedAtBefore(Instant cutoff);
}

