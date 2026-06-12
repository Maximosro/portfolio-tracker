package com.sro.myportfoliotracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "telegram_channel_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramChannelMessage {

  @Id
  @Column(name = "message_id")
  private Long messageId;

  @Column(name = "channel_title", length = 200)
  private String channelTitle;

  @Column(name = "channel_username", length = 200)
  private String channelUsername;

  @Column(name = "text", columnDefinition = "TEXT")
  private String text;

  @Column(name = "date_epoch")
  private Long dateEpoch;

  @Column(name = "received_at")
  private Instant receivedAt;
}

