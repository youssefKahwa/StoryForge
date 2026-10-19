package com.storyforge.orchestrator.topic;

import com.storyforge.orchestrator.dto.CreateSeedTopicRequest;
import com.storyforge.orchestrator.format.VideoFormat;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.run.Run;
import com.storyforge.orchestrator.run.RunService;
import com.storyforge.orchestrator.settings.Preferences;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageUsage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Fills the topic backlog with AI ideas that fit the channel brief, so autopilot never runs dry. */
@Service
public class TopicIdeaService {

    public record Idea(String title, String angle, String why, String format) {
    }

    public record Ideas(List<Idea> topics) {
    }

    private static final Logger log = LoggerFactory.getLogger(TopicIdeaService.class);
    private static final int MAX_COUNT = 10;

    private final LlmRouter llm;
    private final SeedTopicService seedTopics;
    private final RunService runs;
    private final PreferencesService preferences;

    public TopicIdeaService(LlmRouter llm, SeedTopicService seedTopics, RunService runs,
                            PreferencesService preferences) {
        this.llm = llm;
        this.seedTopics = seedTopics;
        this.runs = runs;
        this.preferences = preferences;
    }

    public List<SeedTopic> suggest(int count) {
        int wanted = Math.max(1, Math.min(MAX_COUNT, count));
        Set<String> done = new LinkedHashSet<>();
        runs.list().stream().map(Run::getTopicTitle).filter(t -> t != null && !t.isBlank()).forEach(done::add);
        seedTopics.listActive().forEach(topic -> done.add(topic.getTitle()));
        Preferences prefs = preferences.get();
        String brief = prefs.channelBrief() == null || prefs.channelBrief().isBlank()
                ? PreferencesService.DEFAULT_BRIEF : prefs.channelBrief();
        Ideas ideas = llm.json(new StageUsage(), done.size(), Stage.DISCOVER, "topics", Map.of(
                "channel_brief", brief,
                "done", done.isEmpty() ? "(nothing yet)" : done.stream().map(t -> "- " + t)
                        .collect(Collectors.joining("\n")),
                "count", String.valueOf(wanted)), Ideas.class);
        Set<String> seen = done.stream().map(TopicIdeaService::norm).collect(Collectors.toSet());
        List<SeedTopic> created = new ArrayList<>();
        for (Idea idea : ideas.topics() == null ? List.<Idea>of() : ideas.topics()) {
            if (idea.title() == null || idea.title().isBlank() || !seen.add(norm(idea.title()))
                    || created.size() >= wanted) {
                continue;
            }
            String notes = String.join(" ", nullToEmpty(idea.angle()), idea.why() == null ? "" : "(" + idea.why() + ")")
                    .strip();
            created.add(seedTopics.create(new CreateSeedTopicRequest(idea.title().strip(), notes,
                    format(idea.format(), prefs.defaultFormat()), "en")));
        }
        log.info("Added {} AI topic idea(s) to the backlog", created.size());
        return created;
    }

    private static VideoFormat format(String value, VideoFormat fallback) {
        try {
            return value == null ? fallback : VideoFormat.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static String norm(String title) {
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
