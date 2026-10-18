package com.storyforge.orchestrator.notify;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final StoryForgeProperties.Telegram telegram;
    private final String dashboardUrl;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public NotificationService(StoryForgeProperties properties, JsonMapper json) {
        this.telegram = properties.telegram();
        this.dashboardUrl = properties.dashboardUrl();
        this.json = json;
    }

    public boolean enabled() {
        return telegram != null && notBlank(telegram.botToken()) && notBlank(telegram.chatId());
    }

    public String runLink(long runId) {
        return dashboardUrl + "/run?id=" + runId;
    }

    public void send(String text) {
        if (!enabled()) {
            log.info("Notification (Telegram not configured): {}", text);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.telegram.org/bot" + telegram.botToken() + "/sendMessage"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("chat_id", telegram.chatId(), "text", text)))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Telegram returned {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Telegram notification failed: {}", e.getMessage());
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
