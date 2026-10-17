package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.TopicBrief;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
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
public class OutlineStage implements StageWorker {

    private static final Logger log = LoggerFactory.getLogger(OutlineStage.class);

    private static final double SPEECH_SHARE = 0.9;

    private final LlmRouter llm;
    private final FormatRegistry formats;
    private final JsonMapper json;
    private final int wordsPerMinute;

    public OutlineStage(LlmRouter llm, FormatRegistry formats, JsonMapper json, StoryForgeProperties properties) {
        this.llm = llm;
        this.formats = formats;
        this.json = json;
        this.wordsPerMinute = properties.video().wordsPerMinute();
    }

    @Override
    public Stage stage() {
        return Stage.OUTLINE;
    }

    @Override
    public List<String> outputs() {
        return List.of("outline.json");
    }

    @Override
    public void execute(StageContext context) {
        TopicBrief brief = context.files().read(Stage.SELECT, "topic_brief.json", TopicBrief.class);
        Dossier dossier = context.files().read(Stage.RESEARCH, "dossier.json", Dossier.class);
        FormatDefinition format = formats.get(context.run().format());
        Map<String, String> vars = Map.of(
                "topic", brief.title(),
                "angle", brief.angle(),
                "core_promise", brief.corePromise(),
                "target_seconds", String.valueOf(context.run().targetSeconds()),
                "words_per_minute", String.valueOf(wordsPerMinute),
                "beats", json.writeValueAsString(format.beats()),
                "dossier", json.writeValueAsString(Map.of(
                        "claims", dossier.claims(),
                        "wowFacts", dossier.wowFacts() == null ? List.of() : dossier.wowFacts(),
                        "timeline", dossier.timeline() == null ? List.of() : dossier.timeline())));
        Outline draft = llm.json(context, stage(), "outline", vars, Outline.class);
        if (draft.beats() == null || draft.beats().isEmpty()) {
            throw new IllegalStateException("Outline has no beats");
        }
        draft.beats().stream().filter(beat -> !format.hasBeat(beat.beat()))
                .forEach(beat -> log.warn("Outline beat '{}' is not in format {}", beat.beat(), format.id()));
        context.files().write(Stage.OUTLINE, "outline.json", normalize(draft, context.run().targetSeconds()));
    }

    private Outline normalize(Outline draft, int targetSeconds) {
        double total = draft.beats().stream().mapToInt(beat -> Math.max(1, beat.targetSeconds())).sum();
        List<Outline.Beat> beats = draft.beats().stream().map(beat -> {
            int seconds = (int) Math.round(Math.max(1, beat.targetSeconds()) / total * targetSeconds);
            return beat.withBudget(seconds, (int) Math.round(seconds * wordsPerMinute / 60.0 * SPEECH_SHARE));
        }).toList();
        return new Outline(targetSeconds, beats);
    }
}
