package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.model.AppSetting;
import com.sro.myportfoliotracker.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de notificaciones via Telegram Bot.
 *
 * Telegram es COMPLETAMENTE OPCIONAL. Si no se configura, el servicio
 * permanece inactivo y no produce ningún error ni warning.
 *
 * La configuración se persiste en la tabla app_settings de la BD,
 * por lo que sobrevive a reinicios de la aplicación.
 * También acepta configuración inicial desde application.yaml.
 */
@Service
@Slf4j
public class TelegramService {

    private static final String SETTING_BOT_TOKEN = "telegram.bot-token";
    private static final String SETTING_CHAT_ID = "telegram.chat-id";
    private static final String SETTING_ENABLED = "telegram.enabled";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    private final RestClient restClient;
    private final AlertService alertService;
    private final AppSettingRepository settingRepository;
    private final ActivityLogService activityLog;

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.chat-id:}")
    private String chatId;

    @Value("${telegram.enabled:false}")
    private boolean enabled;

    /**
     * Almacena las alertas ya notificadas para no repetir.
     * Key: "ticker|type|severity" → Value: timestamp de última notificación.
     * Se limpia cuando la alerta desaparece.
     */
    private final Map<String, Instant> notifiedAlerts = new ConcurrentHashMap<>();

    public TelegramService(RestClient restClient, AlertService alertService, AppSettingRepository settingRepository, ActivityLogService activityLog) {
        this.restClient = restClient;
        this.alertService = alertService;
        this.settingRepository = settingRepository;
        this.activityLog = activityLog;
    }

    /**
     * Al iniciar la app, carga la configuración persistida en BD (si existe).
     * Los valores de BD tienen prioridad sobre application.yaml.
     * Si Telegram no está configurado, simplemente no hace nada (sin errores ni warnings).
     */
    @PostConstruct
    public void loadPersistedConfig() {
        try {
            Optional<AppSetting> savedToken = settingRepository.findById(SETTING_BOT_TOKEN);
            Optional<AppSetting> savedChatId = settingRepository.findById(SETTING_CHAT_ID);
            Optional<AppSetting> savedEnabled = settingRepository.findById(SETTING_ENABLED);

            if (savedToken.isPresent() && savedChatId.isPresent()) {
                String dbToken = savedToken.get().getValue();
                String dbChatId = savedChatId.get().getValue();
                if (dbToken != null && !dbToken.isBlank() && dbChatId != null && !dbChatId.isBlank()) {
                    this.botToken = dbToken;
                    this.chatId = dbChatId;
                    this.enabled = savedEnabled.map(s -> "true".equalsIgnoreCase(s.getValue())).orElse(true);
                    log.info("📦 Telegram: configuración cargada desde BD — enabled={}", this.enabled);
                    return;
                }
            }

            // Si no hay config en BD pero sí en yaml y está habilitado, persistirla
            if (isEnabled()) {
                persistConfig();
                log.info("📦 Telegram: configuración de YAML persistida en BD");
            } else {
                // Telegram no configurado: estado normal, no es un error
                log.info("ℹ️ Telegram: no configurado — las notificaciones por Telegram están desactivadas (esto es opcional)");
            }
        } catch (Exception e) {
            // No loguear como warning/error: Telegram es opcional y puede no haber tabla aún
            log.debug("Telegram: no se pudo acceder a la configuración en BD (tabla puede no existir aún): {}", e.getMessage());
            this.enabled = false;
        }
    }

    public boolean isEnabled() {
        return enabled && botToken != null && !botToken.isBlank()
                && chatId != null && !chatId.isBlank();
    }

    /**
     * Configura el bot de Telegram en caliente (sin reiniciar) y persiste en BD.
     */
    public void configure(String newBotToken, String newChatId) {
        this.botToken = newBotToken;
        this.chatId = newChatId;
        this.enabled = true;
        try {
            persistConfig();
        } catch (Exception e) {
            log.debug("Telegram: no se pudo persistir la configuración en BD: {}", e.getMessage());
        }
        log.info("🔧 Telegram configurado y persistido — bot-token: {}…, chat-id: {}",
                newBotToken.substring(0, Math.min(10, newBotToken.length())), newChatId);
        activityLog.info("TELEGRAM", "Telegram configurado correctamente", null, "🔧");
    }

    /**
     * Desactiva Telegram sin borrar la configuración persistida.
     */
    public void disable() {
        this.enabled = false;
        try {
            settingRepository.save(AppSetting.builder().key(SETTING_ENABLED).value("false").build());
        } catch (Exception e) {
            log.debug("Telegram: no se pudo persistir la desactivación en BD: {}", e.getMessage());
        }
        log.info("🔕 Telegram desactivado");
        activityLog.info("TELEGRAM", "Telegram desactivado", null, "🔕");
    }

    private void persistConfig() {
        settingRepository.save(AppSetting.builder().key(SETTING_BOT_TOKEN).value(botToken).build());
        settingRepository.save(AppSetting.builder().key(SETTING_CHAT_ID).value(chatId).build());
        settingRepository.save(AppSetting.builder().key(SETTING_ENABLED).value(String.valueOf(enabled)).build());
    }

    /**
     * Envía un mensaje de texto a través del bot de Telegram.
     * Si Telegram no está configurado/habilitado, retorna false silenciosamente.
     */
    public boolean sendMessage(String text) {
        if (!isEnabled()) {
            return false;
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
                    "text", text,
                    "parse_mode", "HTML",
                    "disable_web_page_preview", true
            );

            restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("📨 Telegram: mensaje enviado");
            activityLog.success("TELEGRAM", "Mensaje enviado a Telegram", null, "📨");
            return true;

        } catch (Exception e) {
            log.warn("Telegram: error enviando mensaje (token o chat_id inválido?): {}", e.getMessage());
            activityLog.error("TELEGRAM", "Error enviando mensaje Telegram: " + e.getMessage(), null, "❌");
            return false;
        }
    }

    /**
     * Envía un mensaje de prueba para verificar la configuración.
     */
    public boolean sendTestMessage() {
        return sendMessage(
                "✅ <b>Portfolio Tracker — Telegram conectado</b>\n\n"
                + "Las notificaciones de alertas están activas.\n"
                + "Recibirás avisos de:\n"
                + "• 🔴 Stop-Loss alcanzado\n"
                + "• 🟢 Take-Profit alcanzado\n"
                + "• 🔵 DCA Target disponible\n"
                + "• ⚠️ Desviación de peso\n"
                + "• 📊 Alertas de precio\n\n"
                + "🕐 " + TIME_FMT.format(Instant.now())
        );
    }

    /**
     * Cada 30 minutos, revisa las alertas y envía notificaciones
     * solo para alertas NUEVAS o que hayan cambiado de severidad.
     * Si Telegram no está configurado, no hace nada (sin errores).
     */
    @Scheduled(cron = "0 5,35 * * * *") // a los :05 y :35 (justo después de la actualización de precios)
    public void checkAndNotifyAlerts() {
        if (!isEnabled()) return;

        try {
            List<AlertDto> currentAlerts = alertService.checkAlerts();
            if (currentAlerts == null || currentAlerts.isEmpty()) {
                notifiedAlerts.clear();
                return;
            }

            Set<String> currentKeys = new HashSet<>();

            for (AlertDto alert : currentAlerts) {
                String key = alert.getTicker() + "|" + alert.getType() + "|" + alert.getSeverity();
                currentKeys.add(key);

                // Solo notificar si es nueva (no se ha notificado antes).
                // Cuando la alerta desaparezca se limpia del mapa, así si vuelve se notifica de nuevo.
                if (notifiedAlerts.containsKey(key)) {
                    continue; // Ya notificada, no repetir
                }

                // Solo notificar alertas DANGER y WARNING (no INFO, para no spammear)
                if ("INFO".equals(alert.getSeverity())) continue;

                String message = formatAlertMessage(alert);
                if (sendMessage(message)) {
                    notifiedAlerts.put(key, Instant.now());
                    activityLog.info("TELEGRAM", "Alerta notificada: " + alert.getTicker() + " — " + alert.getType(), alert.getTicker(), "🔔");
                }

                // Pequeño delay entre mensajes para no saturar la API de Telegram
                Thread.sleep(200);
            }

            // Limpiar alertas que ya no están activas
            notifiedAlerts.keySet().removeIf(key -> !currentKeys.contains(key));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("Telegram: error revisando alertas (se reintentará): {}", e.getMessage());
        }
    }

    private String formatAlertMessage(AlertDto alert) {
        String icon = switch (alert.getSeverity()) {
            case "DANGER" -> "🚨";
            case "WARNING" -> "⚠️";
            default -> "ℹ️";
        };

        String typeLabel = switch (alert.getType()) {
            case "STOP_LOSS" -> "STOP-LOSS";
            case "TAKE_PROFIT" -> "TAKE-PROFIT";
            case "TRAILING_STOP" -> "TRAILING STOP";
            case "DCA_TARGET" -> "DCA TARGET";
            case "ALERT_ABOVE" -> "ALERTA PRECIO ↑";
            case "ALERT_BELOW" -> "ALERTA PRECIO ↓";
            case "WEIGHT_DEVIATION" -> "PESO CARTERA";
            default -> alert.getType();
        };

        return String.format(
                "%s <b>%s — %s</b>\n\n"
                + "%s\n\n"
                + "💰 Precio actual: <b>%.4f €</b>\n"
                + "🕐 %s",
                icon, alert.getTicker(), typeLabel,
                alert.getMessage(),
                alert.getCurrentPrice(),
                TIME_FMT.format(Instant.now())
        );
    }

    /**
     * Envía un resumen diario del portfolio por Telegram.
     * Se ejecuta a las 18:00 (cierre de mercado europeo).
     * Si Telegram no está configurado, no hace nada (sin errores).
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    public void sendDailySummary() {
        if (!isEnabled()) return;

        try {
            List<AlertDto> alerts = alertService.checkAlerts();
            if (alerts == null) alerts = List.of();

            long danger = alerts.stream().filter(a -> "DANGER".equals(a.getSeverity())).count();
            long warning = alerts.stream().filter(a -> "WARNING".equals(a.getSeverity())).count();

            StringBuilder sb = new StringBuilder();
            sb.append("📊 <b>Resumen diario — Portfolio Tracker</b>\n\n");

            if (alerts.isEmpty()) {
                sb.append("✅ Sin alertas activas. Cartera dentro de límites.\n");
            } else {
                sb.append(String.format("⚠️ %d alerta(s) activa(s)", alerts.size()));
                if (danger > 0) sb.append(String.format(" (%d crítica(s))", danger));
                sb.append("\n\n");

                // Solo mostrar las DANGER y WARNING
                alerts.stream()
                        .filter(a -> !"INFO".equals(a.getSeverity()))
                        .limit(5)
                        .forEach(a -> {
                            String icon = "DANGER".equals(a.getSeverity()) ? "🔴" : "🟡";
                            sb.append(String.format("%s <b>%s</b>: %s\n", icon, a.getTicker(), a.getMessage()));
                        });

                if (alerts.size() > 5) {
                    sb.append(String.format("\n... y %d más\n", alerts.size() - 5));
                }
            }

            sb.append("\n🕐 ").append(TIME_FMT.format(Instant.now()));
            sendMessage(sb.toString());
            activityLog.info("TELEGRAM", "Resumen diario enviado por Telegram (" + alerts.size() + " alertas)", null, "📊");

        } catch (Exception e) {
            log.debug("Telegram: error enviando resumen diario (se reintentará mañana): {}", e.getMessage());
        }
    }
}


