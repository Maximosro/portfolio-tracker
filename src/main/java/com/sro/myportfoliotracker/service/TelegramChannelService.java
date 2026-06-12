package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.model.TelegramChannelMessage;
import com.sro.myportfoliotracker.repository.TelegramChannelMessageRepository;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Lee mensajes del canal público t.me/ultimominutoOTC haciendo scraping de la vista web pública. No
 * requiere bot, API key, ni ser admin.
 */
@Service
@Slf4j
public class TelegramChannelService {

  public static final String CHANNEL_USERNAME = "ultimominutoOTC";
  public static final String CHANNEL_URL = "https://t.me/s/" + CHANNEL_USERNAME;

  private static final Pattern MSG_TEXT_PATTERN = Pattern.compile(
      "<div class=\"tgme_widget_message_text[^\"]*\"[^>]*>(.*?)</div>",
      Pattern.DOTALL
  );
  private static final Pattern MSG_DATE_PATTERN = Pattern.compile(
      "<time[^>]*datetime=\"([^\"]+)\"[^>]*>"
  );
  private static final Pattern CHANNEL_TITLE_PATTERN = Pattern.compile(
      "<div class=\"tgme_channel_info_header_title[^\"]*\"[^>]*><span[^>]*>([^<]+)</span>"
  );

  private final TelegramChannelMessageRepository messageRepository;
  private final HttpClient httpClient;

  public TelegramChannelService(TelegramChannelMessageRepository messageRepository) {
    this.messageRepository = messageRepository;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  /**
   * Polling cada 3 minutos: lee la página pública del canal y guarda mensajes nuevos.
   */
  @Scheduled(fixedDelay = 180_000, initialDelay = 15_000)
  public void pollChannelMessages() {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(CHANNEL_URL))
          .header("User-Agent",
              "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
          .header("Accept-Language", "es-ES,es;q=0.9")
          .timeout(Duration.ofSeconds(20))
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        log.debug("Telegram Channel: HTTP {} al leer @{}", response.statusCode(), CHANNEL_USERNAME);
        return;
      }

      String html = response.body();
      List<TelegramChannelMessage> parsed = parseMessages(html);

      int newMessages = 0;
      for (TelegramChannelMessage msg : parsed) {
        if (!messageRepository.existsById(msg.getMessageId())) {
          messageRepository.save(msg);
          newMessages++;
        }
      }

      if (newMessages > 0) {
        log.info("📡 @{}: {} mensajes nuevos", CHANNEL_USERNAME, newMessages);
      }
    } catch (Exception e) {
      log.debug("Telegram Channel: error en polling: {}", e.getMessage());
    }
  }

  private List<TelegramChannelMessage> parseMessages(String html) {
    List<TelegramChannelMessage> messages = new ArrayList<>();

    String channelTitle = CHANNEL_USERNAME;
    Matcher titleMatcher = CHANNEL_TITLE_PATTERN.matcher(html);
    if (titleMatcher.find()) {
      channelTitle = titleMatcher.group(1).trim();
    }

    Pattern fullMsgPattern = Pattern.compile(
        "data-post=\"" + Pattern.quote(CHANNEL_USERNAME) + "/(\\d+)\"(.*?)(?=data-post=|$)",
        Pattern.DOTALL
    );

    Matcher msgMatcher = fullMsgPattern.matcher(html);
    while (msgMatcher.find()) {
      try {
        long messageId = Long.parseLong(msgMatcher.group(1));
        String block = msgMatcher.group(2);

        String text = extractText(block);
        if (text == null || text.isBlank()) {
          continue;
        }

        long dateEpoch = extractDateEpoch(block);

        messages.add(TelegramChannelMessage.builder()
            .messageId(messageId)
            .channelTitle(channelTitle)
            .channelUsername(CHANNEL_USERNAME)
            .text(text)
            .dateEpoch(dateEpoch)
            .receivedAt(Instant.now())
            .build());
      } catch (Exception e) {
        log.trace("Error parseando mensaje: {}", e.getMessage());
      }
    }

    return messages;
  }

  private String extractText(String block) {
    Matcher textMatcher = MSG_TEXT_PATTERN.matcher(block);
    if (textMatcher.find()) {
      return textMatcher.group(1)
          .replaceAll("<br[^>]*>", "\n")
          .replaceAll("<[^>]+>", "")
          .replaceAll("&amp;", "&")
          .replaceAll("&lt;", "<")
          .replaceAll("&gt;", ">")
          .replaceAll("&quot;", "\"")
          .replaceAll("&#39;", "'")
          .replaceAll("&nbsp;", " ")
          .trim();
    }
    return null;
  }

  private long extractDateEpoch(String block) {
    Matcher dateMatcher = MSG_DATE_PATTERN.matcher(block);
    if (dateMatcher.find()) {
      try {
        return OffsetDateTime.parse(dateMatcher.group(1)).toInstant().getEpochSecond();
      } catch (Exception ignored) {
      }
    }
    return Instant.now().getEpochSecond();
  }

  public List<TelegramChannelMessage> getRecentMessages() {
    try {
      return messageRepository.findTop100ByOrderByDateEpochDesc();
    } catch (Exception e) {
      log.debug("Telegram Channel: error leyendo mensajes: {}", e.getMessage());
      return List.of();
    }
  }

  /**
   * Limpia mensajes de más de 7 días. Se ejecuta una vez al día a las 3:00.
   */
  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void cleanOldMessages() {
    try {
      messageRepository.deleteByReceivedAtBefore(Instant.now().minus(7, ChronoUnit.DAYS));
    } catch (Exception e) {
      log.debug("Telegram Channel: error limpiando mensajes: {}", e.getMessage());
    }
  }
}
