package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<AlertDto>> getAlerts() {
        return ResponseEntity.ok(alertService.getTodayAlerts());
    }
}

