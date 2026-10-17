package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.TopicBrief;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageBlockedException;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ResearchStage implements StageWorker {

    private final LlmRouter llm;
    private final int minSources;

    public ResearchStage(LlmRouter llm, StoryForgeProperties properties) {
        this.llm = llm;
        this.minSources = properties.qa().minSources();
    }

    @Override
    public Stage stage() {
        return Stage.RESEARCH;
    }

    @Override
    public List<String> outputs() {
        return List.of("dossier.json", "sources.json");
    }

    @Override
    public void execute(StageContext context) {
        TopicBrief brief = context.files().read(Stage.SELECT, "topic_brief.json", TopicBrief.class);
        Map<String, String> vars = Map.of(
                "topic", brief.title(),
                "angle", brief.angle(),
                "core_promise", brief.corePromise(),
                "min_sources", String.valueOf(minSources));
        Dossier dossier = llm.json(context, stage(), "research", vars, Dossier.class);
        List<String> problems = gate(dossier);
        context.files().write(Stage.RESEARCH, "dossier.json", dossier);
        context.files().write(Stage.RESEARCH, "sources.json", dossier.sources() == null ? List.of() : dossier.sources());
        if (!problems.isEmpty()) {
            throw new StageBlockedException("Research gate: " + String.join("; ", problems));
        }
    }

    private List<String> gate(Dossier dossier) {
        List<String> problems = new ArrayList<>();
        if (dossier.sources() == null || dossier.sources().size() < minSources) {
            problems.add("needs at least " + minSources + " sources");
        }
        if (dossier.claims() == null || dossier.claims().isEmpty()) {
            problems.add("no claims");
            return problems;
        }
        dossier.claims().stream()
                .filter(Dossier.Claim::key)
                .filter(claim -> dossier.independentSourceCount(claim) < 2)
                .forEach(claim -> problems.add("key claim " + claim.id() + " is single-sourced"));
        return problems;
    }
}
