package com.sro.myportfoliotracker.service;

import com.sro.myportfoliotracker.dto.AlertDto;
import com.sro.myportfoliotracker.model.AppSetting;
import com.sro.myportfoliotracker.model.Position;
import com.sro.myportfoliotracker.model.PositionAlert;
import com.sro.myportfoliotracker.model.WatchlistAlert;
import com.sro.myportfoliotracker.model.WatchlistItem;
import com.sro.myportfoliotracker.repository.AppSettingRepository;
import com.sro.myportfoliotracker.repository.PositionAlertRepository;
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
    private final PositionService positionService;
    private final PortfolioMetricsService metricsService;
    private final PositionAlertRepository positionAlertRepository;

    @Value("${telegram.bot-token:}")
    private String botToken;

    @Value("${telegram.chat-id:}")
    private String chatId;

    @Value("${telegram.enabled:false}")
    private boolean enabled;

    public TelegramService(RestClient restClient, AlertService alertService, AppSettingRepository settingRepository,
                           ActivityLogService activityLog, PositionService positionService,
                           PortfolioMetricsService metricsService, PositionAlertRepository positionAlertRepository) {
        this.restClient = restClient;
        this.alertService = alertService;
        this.settingRepository = settingRepository;
        this.activityLog = activityLog;
        this.positionService = positionService;
        this.metricsService = metricsService;
        this.positionAlertRepository = positionAlertRepository;
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
     * Revisa las alertas y envía notificaciones solo para alertas NUEVAS.
     * Usa PositionAlert.notifiedTelegram para evitar reenvíos (persistente).
     * Se invoca automáticamente tras cada actualización de precios.
     * Si Telegram no está configurado, no hace nada (sin errores).
     */
    public void checkAndNotifyAlerts() {
        if (!isEnabled()) return;

        try {
            List<AlertDto> currentAlerts = alertService.getTodayAlerts();
            if (currentAlerts == null || currentAlerts.isEmpty()) {
                return;
            }

            for (AlertDto alert : currentAlerts) {
                if ("INFO".equals(alert.getSeverity())) continue;
                if (alert.getAlertId() == null) continue;

                PositionAlert pa = positionAlertRepository.findById(alert.getAlertId()).orElse(null);
                if (pa == null || Boolean.TRUE.equals(pa.getNotifiedTelegram())) continue;

                String message = formatAlertMessage(alert);
                if (sendMessage(message)) {
                    pa.setNotifiedTelegram(true);
                    positionAlertRepository.save(pa);
                    activityLog.info("TELEGRAM", "Alerta notificada: " + alert.getTicker() + " — " + alert.getType(), alert.getTicker(), "🔔");
                }
            }

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
     * Envía una notificación de alerta de watchlist por Telegram.
     */
    public boolean sendWatchlistAlert(WatchlistItem item, WatchlistAlert alert, String message) {
        if (!isEnabled()) return false;

        String icon = switch (alert.getAlertType()) {
            case PRICE_ABOVE -> "📈";
            case PRICE_BELOW -> "📉";
            case VOLUME_ABOVE -> "🔥";
            case VOLUME_BELOW -> "🧊";
        };

        String typeLabel = switch (alert.getAlertType()) {
            case PRICE_ABOVE -> "ALERTA PRECIO ↑";
            case PRICE_BELOW -> "ALERTA PRECIO ↓";
            case VOLUME_ABOVE -> "ALERTA VOLUMEN ALTO";
            case VOLUME_BELOW -> "ALERTA VOLUMEN BAJO";
        };

        String text = String.format(
                "%s <b>👁 WATCHLIST — %s — %s</b>\n\n%s\n\n💰 Precio actual: <b>%.2f €</b>\n🕐 %s",
                icon, item.getTicker(), typeLabel, message,
                item.getCurrentPrice(), TIME_FMT.format(Instant.now())
        );

        boolean sent = sendMessage(text);
        if (sent) {
            activityLog.info("TELEGRAM", "Alerta watchlist: " + item.getTicker() + " — " + typeLabel, item.getTicker(), "👁");
        }
        return sent;
    }

    /**
     * Envía un informe de cierre de mercado por Telegram.
     * Se ejecuta a las 18:00 L-V (cierre de mercado europeo).
     * Incluye: valor total, P&L del día, top gainers/losers, TIR y alertas.
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    public void sendDailySummary() {
        if (!isEnabled()) return;

        try {
            List<Position> positions = positionService.findAll().stream()
                    .filter(p -> p.getShares() != null && p.getShares() > 0)
                    .filter(p -> p.getCurrentPrice() != null && p.getCurrentPrice() > 0)
                    .toList();

            if (positions.isEmpty()) return;

            // ── Valor total y coste total ──
            double totalValue = 0, totalCost = 0, dayPL = 0;
            List<double[]> dayChanges = new ArrayList<>(); // [changePct, ticker index]

            for (int i = 0; i < positions.size(); i++) {
                Position p = positions.get(i);
                double value = p.getShares() * p.getCurrentPrice();
                double cost = p.getShares() * (p.getAvgPrice() != null ? p.getAvgPrice() : p.getCurrentPrice());
                totalValue += value;
                totalCost += cost;

                // Variación diaria
                if (p.getPreviousClose() != null && p.getPreviousClose() > 0) {
                    double changePct = ((p.getCurrentPrice() - p.getPreviousClose()) / p.getPreviousClose()) * 100;
                    double changeAbs = p.getShares() * (p.getCurrentPrice() - p.getPreviousClose());
                    dayPL += changeAbs;
                    dayChanges.add(new double[]{changePct, i});
                }
            }

            double totalPL = totalValue - totalCost;
            double totalPLPct = totalCost > 0 ? (totalPL / totalCost) * 100 : 0;

            StringBuilder sb = new StringBuilder();
            sb.append("📊 <b>Cierre de mercado — Portfolio Tracker</b>\n\n");

            // ── Resumen de cartera ──
            sb.append(String.format("💼 <b>Valor total:</b> %s €\n", fmtMoney(totalValue)));
            sb.append(String.format("💰 <b>P&amp;L total:</b> %s € (%s%%)\n",
                    fmtMoneySign(totalPL), fmtSign(totalPLPct)));

            if (!dayChanges.isEmpty()) {
                String dayIcon = dayPL >= 0 ? "📈" : "📉";
                sb.append(String.format("%s <b>Hoy:</b> %s €\n", dayIcon, fmtMoneySign(dayPL)));
            }

            // ── TIR (XIRR) ──
            try {
                var metrics = metricsService.calculateMetrics();
                if (metrics.portfolioXirr() != null) {
                    sb.append(String.format("🎯 <b>TIR (XIRR):</b> %s%%\n", fmtSign(metrics.portfolioXirr() * 100)));
                }
                if (metrics.totalRealizedPL() != null && metrics.totalRealizedPL() != 0) {
                    sb.append(String.format("🏦 <b>P&amp;L realizado:</b> %s €\n", fmtMoneySign(metrics.totalRealizedPL())));
                }
            } catch (Exception ignored) {}

            // ── Top movers del día ──
            if (dayChanges.size() >= 2) {
                dayChanges.sort((a, b) -> Double.compare(b[0], a[0]));

                sb.append("\n<b>🟢 Mejor hoy:</b>\n");
                int topN = Math.min(3, dayChanges.size());
                for (int i = 0; i < topN; i++) {
                    Position p = positions.get((int) dayChanges.get(i)[1]);
                    double pct = dayChanges.get(i)[0];
                    sb.append(String.format("  %s  %s%%\n", p.getTicker(), fmtSign(pct)));
                }

                sb.append("\n<b>🔴 Peor hoy:</b>\n");
                for (int i = dayChanges.size() - 1; i >= Math.max(0, dayChanges.size() - topN); i--) {
                    Position p = positions.get((int) dayChanges.get(i)[1]);
                    double pct = dayChanges.get(i)[0];
                    if (pct >= 0 && i < dayChanges.size() - 1) break; // todas positivas, no listar
                    sb.append(String.format("  %s  %s%%\n", p.getTicker(), fmtSign(pct)));
                }
            }

            // ── Alertas activas ──
            List<AlertDto> alerts = alertService.checkAlerts();
            if (alerts != null && !alerts.isEmpty()) {
                List<AlertDto> dangerAlerts = alerts.stream().filter(a -> "DANGER".equals(a.getSeverity())).toList();
                List<AlertDto> warningAlerts = alerts.stream().filter(a -> "WARNING".equals(a.getSeverity())).toList();

                if (!dangerAlerts.isEmpty()) {
                    sb.append(String.format("\n🚨 <b>%d alerta(s) crítica(s):</b>\n", dangerAlerts.size()));
                    dangerAlerts.forEach(a -> sb.append(String.format("  🔴 <b>%s</b>: %s\n", a.getTicker(), a.getMessage())));
                }

                if (!warningAlerts.isEmpty()) {
                    sb.append(String.format("\n⚠️ <b>%d aviso(s):</b>\n", warningAlerts.size()));
                    warningAlerts.forEach(a -> sb.append(String.format("  🟡 <b>%s</b>: %s\n", a.getTicker(), a.getMessage())));
                }
            } else {
                sb.append("\n✅ Sin alertas activas\n");
            }

            sb.append(String.format("\n📍 %d posiciones · 🕐 %s", positions.size(), TIME_FMT.format(Instant.now())));

            sendMessage(sb.toString());
            activityLog.info("TELEGRAM", "Informe de cierre enviado por Telegram", null, "📊");

        } catch (Exception e) {
            log.debug("Telegram: error enviando informe de cierre: {}", e.getMessage());
        }
    }

    private static String fmtMoney(double v) {
        return String.format("%,.2f", v);
    }

    private static String fmtMoneySign(double v) {
        return String.format("%+,.2f", v);
    }

    private static String fmtSign(double v) {
        return String.format("%+.2f", v);
    }
}


