package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.service.NewsService;
import com.sro.myportfoliotracker.service.NewsService.NewsItem;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

  private final NewsService newsService;

  @GetMapping
  public ResponseEntity<?> getNews() {
    try {
      List<NewsItem> news = newsService.fetchNews();
      return ResponseEntity.ok(Map.of("items", news, "error", false));
    } catch (Exception e) {
      return ResponseEntity.internalServerError()
          .body(Map.of("items", List.of(), "error", true,
              "message", "Error al obtener noticias: " + e.getMessage()));
    }
  }
}
