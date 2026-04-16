package com.sro.myportfoliotracker.controller;

import com.sro.myportfoliotracker.model.TelegramChannelMessage;
import com.sro.myportfoliotracker.service.TelegramChannelService;
import com.sro.myportfoliotracker.service.TelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controlador para la integración opcional con Telegram.
 * Todos los endpoints son seguros incluso cuando Telegram no está configurado.
 */
@RestController
@RequestMapping("/api/telegram")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TelegramController {

    private final TelegramService telegramService;
    private final TelegramChannelService channelService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM, HH:mm")
            .withZone(ZoneId.of("Europe/Madrid"))
            .withLocale(new Locale("es", "ES"));

    // ── Bot de notificaciones (existente) ─────────────────────────────

    /**
     * Devuelve el estado de la configuración de Telegram.
     * Nunca lanza excepciones.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        try {
            boolean isEnabled = telegramService.isEnabled();
            return ResponseEntity.ok(Map.of(
                    "enabled", isEnabled,
                    "configured", isEnabled
            ));
        } catch (Exception e) {
            // Si hay cualquier error, simplemente reportar como no configurado
            return ResponseEntity.ok(Map.of(
                    "enabled", false,
                    "configured", false
            ));
        }
    }

    /**
     * Envía un mensaje de prueba para verificar que la configuración funciona.
     */
    @PostMapping("/test")
    public ResponseEntity<?> sendTest() {
        try {
            if (!telegramService.isEnabled()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Telegram no está configurado. Usa el botón 'Configurar' para añadir tu Bot Token y Chat ID."
                ));
            }

            boolean sent = telegramService.sendTestMessage();
            if (sent) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Mensaje de prueba enviado. ¡Revisa tu Telegram!"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Error al enviar el mensaje. Revisa el token y el chat_id."
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Error de conexión con Telegram: " + e.getMessage()
            ));
        }
    }

    /**
     * Configura Telegram en caliente (sin reiniciar la app).
     */
    @PostMapping("/configure")
    public ResponseEntity<?> configure(@RequestBody Map<String, String> body) {
        try {
            String botToken = body.get("botToken");
            String chatId = body.get("chatId");

            if (botToken == null || botToken.isBlank() || chatId == null || chatId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Debes indicar botToken y chatId"
                ));
            }

            telegramService.configure(botToken.trim(), chatId.trim());

            // Enviar test automáticamente para validar
            boolean sent = telegramService.sendTestMessage();
            if (sent) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Telegram configurado y mensaje de prueba enviado. ¡Revisa tu Telegram!"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Configuración guardada, pero no se pudo enviar el mensaje de prueba. Revisa el token y el chat_id."
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Error al configurar Telegram: " + e.getMessage()
            ));
        }
    }

    /**
     * Envía un mensaje personalizado por Telegram.
     */
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, String> body) {
        try {
            String text = body.get("message");
            if (text == null || text.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El mensaje no puede estar vacío"));
            }

            if (!telegramService.isEnabled()) {
                return ResponseEntity.ok(Map.of("sent", false, "message", "Telegram no configurado"));
            }

            boolean sent = telegramService.sendMessage(text);
            return ResponseEntity.ok(Map.of("sent", sent));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("sent", false, "message", "Error: " + e.getMessage()));
        }
    }

    /**
     * Desactiva Telegram (sin borrar la configuración).
     */
    @PostMapping("/disable")
    public ResponseEntity<?> disable() {
        try {
            telegramService.disable();
            return ResponseEntity.ok(Map.of("success", true, "message", "Telegram desactivado"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Telegram desactivado"));
        }
    }

    // ── Canal @ultimominutoOTC ─────────────────────────────────────────

    /**
     * Devuelve los últimos mensajes del canal suscrito.
     */
    @GetMapping("/channel/messages")
    public ResponseEntity<?> getChannelMessages() {
        try {
            List<TelegramChannelMessage> messages = channelService.getRecentMessages();

            List<Map<String, Object>> items = messages.stream().map(m -> Map.<String, Object>of(
                    "messageId", m.getMessageId(),
                    "channelTitle", m.getChannelTitle() != null ? m.getChannelTitle() : "",
                    "text", m.getText() != null ? m.getText() : "",
                    "date", m.getDateEpoch() != null ? DATE_FMT.format(Instant.ofEpochSecond(m.getDateEpoch())) : "",
                    "dateEpoch", m.getDateEpoch() != null ? m.getDateEpoch() : 0
            )).toList();

            return ResponseEntity.ok(Map.of(
                    "items", items,
                    "channel", TelegramChannelService.CHANNEL_USERNAME
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("items", List.of(), "channel", TelegramChannelService.CHANNEL_USERNAME));
        }
    }

    /**
     * Fuerza un polling inmediato al canal (sin esperar al scheduled).
     */
    @PostMapping("/channel/poll")
    public ResponseEntity<?> pollNow() {
        try {
            channelService.pollChannelMessages();
            return ResponseEntity.ok(Map.of("success", true, "messageCount", channelService.getRecentMessages().size()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }
}
