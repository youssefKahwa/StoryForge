package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.dto.CreateRunRequest;
import com.storyforge.orchestrator.notify.NotificationService;
import com.storyforge.orchestrator.settings.SettingsService;
import com.storyforge.orchestrator.topic.SeedTopic;
import com.storyforge.orchestrator.topic.SeedTopicService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Autopilot {

    private static final Logger log = LoggerFactory.getLogger(Autopilot.class);

    private final SeedTopicService seedTopics;
    private final RunService runs;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final boolean enabled;

    public Autopilot(SeedTopicService seedTopics, RunService runs, SettingsService settings,
                     NotificationService notifications, StoryForgeProperties properties) {
        this.seedTopics = seedTopics;
        this.runs = runs;
        this.settings = settings;
        this.notifications = notifications;
        this.enabled = properties.autopilot().enabled();
    }

    @Scheduled(cron = "${storyforge.autopilot.cron}")
    public void weekly() {
        if (!enabled || settings.isPaused()) {
            return;
        }
        List<SeedTopic> backlog = seedTopics.backlog();
        if (backlog.isEmpty()) {
            notifications.send("StoryForge autopilot: the topic backlog is empty, add a topic or use New video.");
            return;
        }
        SeedTopic topic = backlog.get(0);
        Run run = runs.create(new CreateRunRequest(topic.getId(), null, null, null, null, null, null, null));
        log.info("Autopilot started run {} for '{}'", run.getId(), topic.getTitle());
        notifications.send("StoryForge autopilot started run #" + run.getId() + ": " + topic.getTitle() + "\n"
                + notifications.runLink(run.getId()));
    }
}
