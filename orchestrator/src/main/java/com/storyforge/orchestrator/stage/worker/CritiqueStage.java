package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Critique;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.script.SfmParseException;
import com.storyforge.orchestrator.script.SfmParser;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CritiqueStage implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(CritiqueStage.class);

    private record Version(String markup, List<Scene> scenes, Critique critique) {
    }

    private final LlmRouter llm;
    private final JsonMapper json;
    private final StoryForgeProperties properties;

    public CritiqueStage(LlmRouter llm, JsonMapper json, StoryForgeProperties properties) {
        this.llm = llm;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public Stage stage() {
        return Stage.CRITIQUE;
    }

    @Override
    public List<String> outputs() {
        return List.of("critique.json", "script.sfm.md", "scenes.json");
    }

    @Override
    public void execute(StageContext context) {
        Outline outline = context.files().read(Stage.OUTLINE, "outline.json", Outline.class);
        Dossier dossier = context.files().read(Stage.RESEARCH, "dossier.json", Dossier.class);
        String outlineJson = json.writeValueAsString(outline.beats());
        String markup = context.files().readText(Stage.SCRIPT, "script.sfm.md");

        Version best = null;
        int maxRewrites = properties.llm().maxRewrites();
        for (int round = 0; round <= maxRewrites; round++) {
            Critique critique = llm.json(context, stage(), "critique",
                            Map.of("script", markup, "outline", outlineJson), Critique.class)
                    .evaluated(properties.qa().criticAverage(), properties.qa().criticHook(), round);
            Version version = new Version(markup, SfmParser.parse(markup), critique);
            if (best == null || critique.average() > best.critique().average()) {
                best = version;
            }
            if (critique.passed() || round == maxRewrites) {
                break;
            }
            log.info("Run {} critique round {} below threshold (avg {}, hook {}), rewriting", context.run().id(),
                    round, critique.average(), critique.hook());
            String rewritten = llm.text(context, Stage.SCRIPT, "rewrite", Map.of(
                    "script", markup,
                    "critique", json.writeValueAsString(critique),
                    "outline", outlineJson,
                    "claims", json.writeValueAsString(dossier.claims())));
            try {
                SfmParser.parse(rewritten);
                markup = rewritten;
            } catch (SfmParseException e) {
                log.warn("Rewrite produced invalid markup, keeping previous version: {}", e.getMessage());
            }
        }
        context.files().write(Stage.CRITIQUE, "critique.json", best.critique());
        context.files().writeText(Stage.CRITIQUE, "script.sfm.md", best.markup());
        context.files().write(Stage.CRITIQUE, "scenes.json", best.scenes());
    }
}
