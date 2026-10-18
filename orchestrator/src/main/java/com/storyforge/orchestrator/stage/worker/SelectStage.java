package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.model.TopicBrief;
import com.storyforge.orchestrator.model.TopicCandidate;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.run.RunInfo;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SelectStage implements StageWorker {

    private final LlmRouter llm;
    private final JsonMapper json;

    public SelectStage(LlmRouter llm, JsonMapper json) {
        this.llm = llm;
        this.json = json;
    }

    private String inspirationNotes(StageContext context) {
        if (!context.files().exists(Stage.DISCOVER, DiscoverStage.INSPIRATION)) {
            return "none";
        }
        InspirationIdeas ideas = context.files().read(Stage.DISCOVER, DiscoverStage.INSPIRATION, InspirationIdeas.class);
        return json.writeValueAsString(Map.of("story", ideas.story(),
                "whatWorked", ideas.whatWorked() == null ? List.of() : ideas.whatWorked(),
                "cautions", ideas.cautions() == null ? List.of() : ideas.cautions()));
    }

    @Override
    public Stage stage() {
        return Stage.SELECT;
    }

    @Override
    public List<String> outputs() {
        return List.of("topic_brief.json");
    }

    @Override
    public void execute(StageContext context) {
        RunInfo run = context.run();
        List<TopicCandidate> candidates = context.files().read(Stage.DISCOVER, "candidates.json",
                new TypeReference<List<TopicCandidate>>() { });
        Map<String, String> vars = Map.of(
                "topic", run.topicTitle(),
                "format", run.format().id(),
                "language", run.language(),
                "target_minutes", String.valueOf(Math.round(run.targetSeconds() / 60.0)),
                "candidates", json.writeValueAsString(candidates.stream().map(TopicCandidate::title).toList()),
                "owner_angle", run.angle() != null ? run.angle() : "none, choose the strongest",
                "inspiration", inspirationNotes(context));
        TopicBrief brief = llm.json(context, stage(), "select", vars, TopicBrief.class);
        if (brief.angle() == null || brief.corePromise() == null) {
            throw new IllegalStateException("Topic brief is missing angle or corePromise");
        }
        context.files().write(Stage.SELECT, "topic_brief.json",
                brief.withRunFacts(run.topicTitle(), run.format(), run.language(), run.targetSeconds()));
    }
}
