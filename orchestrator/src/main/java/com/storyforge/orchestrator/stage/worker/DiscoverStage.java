package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.inspiration.InspirationFiles;
import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.model.TopicCandidate;
import com.storyforge.orchestrator.run.RunInfo;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import com.storyforge.orchestrator.topic.SeedTopicService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DiscoverStage implements StageWorker {

    private static final int MAX_BACKLOG = 10;

    public static final String INSPIRATION = "inspiration.json";

    private final SeedTopicService seedTopics;
    private final InspirationFiles inspirations;

    public DiscoverStage(SeedTopicService seedTopics, InspirationFiles inspirations) {
        this.seedTopics = seedTopics;
        this.inspirations = inspirations;
    }

    @Override
    public Stage stage() {
        return Stage.DISCOVER;
    }

    @Override
    public List<String> outputs() {
        return List.of("candidates.json");
    }

    @Override
    public void execute(StageContext context) {
        RunInfo run = context.run();
        List<TopicCandidate> candidates = new ArrayList<>();
        candidates.add(new TopicCandidate(run.topicTitle(), "owner", run.format(), run.language(), run.angle(), 1.0,
                true));
        if (run.inspirationId() != null) {
            InspirationIdeas ideas = inspirations.ideas(run.inspirationId()).orElseThrow(
                    () -> new IllegalStateException("Inspiration " + run.inspirationId() + " has no analysed ideas"));
            context.files().write(Stage.DISCOVER, INSPIRATION, ideas);
            ideas.angles().forEach(angle -> candidates.add(new TopicCandidate(angle.title(), "inspiration",
                    run.format(), run.language(), angle.angle(), 0.8, false)));
        }
        seedTopics.backlog().stream()
                .filter(seed -> !seed.getTitle().equalsIgnoreCase(run.topicTitle()))
                .limit(MAX_BACKLOG)
                .forEach(seed -> candidates.add(new TopicCandidate(seed.getTitle(), "seed_backlog",
                        seed.getFormat(), seed.getLanguage(), seed.getNotes(), 0.5, false)));
        context.usage().provider("seed-backlog");
        context.files().write(Stage.DISCOVER, "candidates.json", candidates);
    }
}
