package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.dto.CreateInspirationRequest;
import com.storyforge.orchestrator.dto.CreateRunRequest;
import com.storyforge.orchestrator.inspiration.Inspiration;
import com.storyforge.orchestrator.inspiration.InspirationFinished;
import com.storyforge.orchestrator.inspiration.InspirationService;
import com.storyforge.orchestrator.inspiration.InspirationStatus;
import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.notify.NotificationService;
import com.storyforge.orchestrator.topic.SeedTopic;
import com.storyforge.orchestrator.topic.SeedTopicService;
import com.storyforge.orchestrator.topic.TopicIdeaService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * One-box start: links, a topic, both, or nothing at all. Links are analysed first and the run starts by itself
 * with the strongest angle; nothing at all takes the next backlog topic (asking the AI for ideas when it is empty).
 */
@Service
public class QuickStartService {

    public record Result(Long runId, Long inspirationId, String message) {
    }

    private static final Logger log = LoggerFactory.getLogger(QuickStartService.class);
    private static final int IDEAS_WHEN_EMPTY = 5;

    private final InspirationService inspirations;
    private final RunService runs;
    private final SeedTopicService seedTopics;
    private final TopicIdeaService ideas;
    private final NotificationService notifications;

    public QuickStartService(InspirationService inspirations, RunService runs, SeedTopicService seedTopics,
                             TopicIdeaService ideas, NotificationService notifications) {
        this.inspirations = inspirations;
        this.runs = runs;
        this.seedTopics = seedTopics;
        this.ideas = ideas;
        this.notifications = notifications;
    }

    public Result start(List<String> urls, String pastedText, String topic, String notes) {
        return start(urls, pastedText, topic, notes, null);
    }

    /** mainUrl: the owner's main video, whose script the new one follows part by part. */
    public Result start(List<String> urls, String pastedText, String topic, String notes, String mainUrl) {
        List<String> links = urls == null ? List.of() : urls.stream().filter(u -> u != null && !u.isBlank()).toList();
        boolean pasted = pastedText != null && !pastedText.isBlank();
        if (!links.isEmpty() || pasted || !blank(mainUrl)) {
            String ownerNotes = join(blank(topic) ? null : "Focus on: " + topic.strip(), notes);
            Inspiration inspiration = inspirations.create(new CreateInspirationRequest(links, pastedText, mainUrl),
                    true, ownerNotes);
            return new Result(null, inspiration.getId(), "Reading the videos. The run starts by itself when the "
                    + "analysis is ready (a few minutes).");
        }
        if (!blank(topic)) {
            Run run = runs.create(new CreateRunRequest(null, topic.strip(), null, null, null, null, notes, null));
            return new Result(run.getId(), null, "Run #" + run.getId() + " started.");
        }
        return startNextTopic(notes);
    }

    /** Next backlog topic; if the backlog is empty, the AI fills it first. Used by Quick start and autopilot. */
    public Result startNextTopic(String notes) {
        List<SeedTopic> backlog = seedTopics.backlog();
        if (backlog.isEmpty()) {
            ideas.suggest(IDEAS_WHEN_EMPTY);
            backlog = seedTopics.backlog();
        }
        if (backlog.isEmpty()) {
            throw new IllegalStateException("No topic available: the AI suggested nothing new. Add a topic.");
        }
        SeedTopic topic = backlog.get(0);
        Run run = runs.create(new CreateRunRequest(topic.getId(), null, null, null, null, null, notes, null));
        return new Result(run.getId(), null, "Run #" + run.getId() + " started: " + topic.getTitle());
    }

    @EventListener
    public void onInspirationFinished(InspirationFinished event) {
        Inspiration inspiration = inspirations.get(event.id());
        if (!inspiration.isAutoStart()) {
            return;
        }
        if (event.status() != InspirationStatus.READY) {
            notifications.send("StoryForge Quick start: the videos could not be read (" + event.error()
                    + "). Paste a transcript in New video instead.");
            return;
        }
        try {
            InspirationIdeas analysed = inspirations.ideas(event.id()).orElseThrow();
            InspirationIdeas.Angle best = analysed.angles().get(0);
            String angle = join(best.title() + ": " + best.angle(), inspiration.getOwnerNotes() == null ? null
                    : "Owner notes: " + inspiration.getOwnerNotes());
            Run run = runs.create(new CreateRunRequest(null, analysed.topic(), analysed.suggestedFormat(), null,
                    null, event.id(), angle, null));
            log.info("Quick start: inspiration {} -> run {} ({})", event.id(), run.getId(), best.title());
            notifications.send("StoryForge Quick start: run #" + run.getId() + " started: " + analysed.topic()
                    + " (" + best.title() + ")\n" + notifications.runLink(run.getId()));
        } catch (RuntimeException e) {
            log.error("Quick start failed for inspiration {}", event.id(), e);
            notifications.send("StoryForge Quick start could not start the run: " + e.getMessage());
        }
    }

    private static String join(String first, String second) {
        if (blank(first)) {
            return blank(second) ? null : second.strip();
        }
        return blank(second) ? first.strip() : first.strip() + "\n" + second.strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
