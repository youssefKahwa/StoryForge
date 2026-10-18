package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.dto.YouTubeStatusDto;
import com.storyforge.orchestrator.notify.NotificationService;
import com.storyforge.orchestrator.youtube.YouTubeAuthService;
import com.storyforge.orchestrator.youtube.YouTubeClient;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/youtube")
public class YouTubeController {

    private final YouTubeAuthService auth;
    private final YouTubeClient client;
    private final NotificationService notifications;
    private final StoryForgeProperties properties;

    public YouTubeController(YouTubeAuthService auth, YouTubeClient client, NotificationService notifications,
                             StoryForgeProperties properties) {
        this.auth = auth;
        this.client = client;
        this.notifications = notifications;
        this.properties = properties;
    }

    @GetMapping("/status")
    public YouTubeStatusDto status() {
        String channel = null;
        if (auth.connected()) {
            try {
                channel = client.channelTitle();
            } catch (RuntimeException e) {
                channel = "error: " + e.getMessage();
            }
        }
        return YouTubeStatusDto.from(auth.configured(), auth.connected(), channel, properties.providers().publish(),
                auth.redirectUri(), notifications.enabled(), properties.autopilot().enabled());
    }

    @GetMapping("/oauth/start")
    public ResponseEntity<Void> start() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(auth.authorizationUrl())).build();
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String result = "connected";
        if (error != null || code == null) {
            result = "denied";
        } else {
            auth.completeAuthorization(state, code);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(properties.dashboardUrl() + "/settings?youtube=" + result)).build();
    }

    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect() {
        auth.disconnect();
    }

    @PostMapping("/test-notification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void testNotification() {
        notifications.send("StoryForge: notifications work.");
    }
}
