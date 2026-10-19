package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.notify.NotificationService;
import com.storyforge.orchestrator.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Autopilot {

    private static final Logger log = LoggerFactory.getLogger(Autopilot.class);

    private final QuickStartService quickStart;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final boolean enabled;

    public Autopilot(QuickStartService quickStart, SettingsService settings,
                     NotificationService notifications, StoryForgeProperties properties) {
        this.quickStart = quickStart;
        this.settings = settings;
        this.notifications = notifications;
        this.enabled = properties.autopilot().enabled();
    }

    @Scheduled(cron = "${storyforge.autopilot.cron}")
    public void weekly() {
        if (!enabled || settings.isPaused()) {
            return;
        }
        try {
            QuickStartService.Result result = quickStart.startNextTopic(null);
            log.info("Autopilot: {}", result.message());
            notifications.send("StoryForge autopilot: " + result.message() + "\n"
                    + notifications.runLink(result.runId()));
        } catch (RuntimeException e) {
            log.error("Autopilot could not start a run", e);
            notifications.send("StoryForge autopilot could not start a run: " + e.getMessage());
        }
    }
}
