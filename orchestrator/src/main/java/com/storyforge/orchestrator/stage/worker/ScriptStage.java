package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.TopicBrief;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.script.NarrationStyle;
import com.storyforge.orchestrator.script.SfmParser;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ScriptStage implements StageWorker {

    private final LlmRouter llm;
    private final JsonMapper json;
    private final NarrationStyle style;

    public ScriptStage(LlmRouter llm, JsonMapper json, NarrationStyle style) {
        this.llm = llm;
        this.json = json;
        this.style = style;
    }

    @Override
    public Stage stage() {
        return Stage.SCRIPT;
    }

    @Override
    public List<String> outputs() {
        return List.of("script.sfm.md", "scenes.json");
    }

    @Override
    public void execute(StageContext context) {
        TopicBrief brief = context.files().read(Stage.SELECT, "topic_brief.json", TopicBrief.class);
        Outline outline = context.files().read(Stage.OUTLINE, "outline.json", Outline.class);
        Dossier dossier = context.files().read(Stage.RESEARCH, "dossier.json", Dossier.class);
        int totalWords = outline.beats().stream().mapToInt(Outline.Beat::wordBudget).sum();
        Map<String, String> vars = new HashMap<>(style.vars(context));
        vars.putAll(Map.of(
                "topic", brief.title(),
                "angle", brief.angle(),
                "outline", json.writeValueAsString(outline.spine() == null ? outline.beats() : outline),
                "claims", json.writeValueAsString(dossier.claims().stream()
                        .map(claim -> Map.of("id", claim.id(), "text", claim.text(), "contested", claim.contested()))
                        .toList()),
                "total_words", String.valueOf(totalWords)));
        String markup = llm.text(context, stage(), "script", vars);
        List<Scene> scenes = SfmParser.parse(markup);
        context.files().writeText(Stage.SCRIPT, "script.sfm.md", markup);
        context.files().write(Stage.SCRIPT, "scenes.json", scenes);
    }
}
