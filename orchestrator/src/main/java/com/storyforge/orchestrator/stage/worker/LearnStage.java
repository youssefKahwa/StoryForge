package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.LearningPlan;
import com.storyforge.orchestrator.model.Publication;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LearnStage implements StageWorker {

    @Override
    public Stage stage() {
        return Stage.LEARN;
    }

    @Override
    public List<String> outputs() {
        return List.of("learning.json");
    }

    @Override
    public void execute(StageContext context) {
        Publication publication = context.files().read(Stage.PUBLISH, "publication.json", Publication.class);
        Instant base = publication.publishAt() != null ? publication.publishAt() : publication.uploadedAt();
        List<LearningPlan.Pull> pulls = List.of(
                new LearningPlan.Pull("+48h", base.plus(Duration.ofHours(48)), "scheduled"),
                new LearningPlan.Pull("+7d", base.plus(Duration.ofDays(7)), "scheduled"),
                new LearningPlan.Pull("+28d", base.plus(Duration.ofDays(28)), "scheduled"));
        context.usage().provider("learning-planner");
        context.files().write(Stage.LEARN, "learning.json",
                new LearningPlan(publication.videoId(), "scheduled", pulls));
    }
}
