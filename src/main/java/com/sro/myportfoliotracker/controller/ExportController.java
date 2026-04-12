package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.service.ExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/export")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /**
     * Genera y descarga un informe Markdown completo del portfolio,
     * optimizado para ser usado como contexto en conversaciones con IA (Claude).
     */
    @GetMapping(produces = "text/markdown; charset=UTF-8")
    public ResponseEntity<byte[]> exportReport() {
        String report = exportService.generateReport();
        byte[] bytes = report.getBytes(StandardCharsets.UTF_8);

        String filename = "portfolio_report_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".md";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/markdown; charset=UTF-8")
                .contentLength(bytes.length)
                .body(bytes);
    }

    /**
     * Devuelve el informe como texto plano (para previsualización en el navegador).
     */
    @GetMapping(value = "/preview", produces = MediaType.TEXT_PLAIN_VALUE + "; charset=UTF-8")
    public ResponseEntity<String> previewReport() {
        return ResponseEntity.ok(exportService.generateReport());
    }
}

