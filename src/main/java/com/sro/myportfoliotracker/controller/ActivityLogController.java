package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.ActivityLogEntry;
import com.sro.myportfoliotracker.service.ActivityLogService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity-log")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ActivityLogController {

  private final ActivityLogService activityLogService;

  @GetMapping
  public ResponseEntity<List<ActivityLogEntry>> getLog(
      @RequestParam(required = false) String category,
      @RequestParam(required = false, defaultValue = "200") int limit) {
    List<ActivityLogEntry> entries;
    if (category != null && !category.isBlank()) {
      entries = activityLogService.getByCategory(category);
    } else {
      entries = activityLogService.getLatest(limit);
    }
    return ResponseEntity.ok(entries);
  }

  @DeleteMapping
  public ResponseEntity<Map<String, String>> clearLog() {
    activityLogService.clear();
    return ResponseEntity.ok(Map.of("status", "ok"));
  }
}

