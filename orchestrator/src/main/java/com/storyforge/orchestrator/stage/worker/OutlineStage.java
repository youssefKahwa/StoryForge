package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.InspirationIdeas;
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

    // Scripts now have few pauses (one per scene at most), so almost all the runtime is speech.
    private static final double SPEECH_SHARE = 0.97;

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
        InspirationIdeas ideas = context.files().exists(Stage.DISCOVER, DiscoverStage.INSPIRATION)
                ? context.files().read(Stage.DISCOVER, DiscoverStage.INSPIRATION, InspirationIdeas.class) : null;
        boolean blueprint = ideas != null && ideas.hasBlueprint();
        // With inspiration links, their running order IS the beat sheet: the generic format beats (hook, recap,
        // pillars...) are not even shown, so the model cannot fall back to an essay structure.
        Object beatSheet = blueprint ? blueprintBeats(ideas) : format.beats();
        Map<String, String> vars = Map.of(
                "blueprint", blueprint ? json.writeValueAsString(ideas.blueprint()) : "[]",
                "topic", brief.title(),
                "angle", brief.angle(),
                "core_promise", brief.corePromise(),
                "target_seconds", String.valueOf(context.run().targetSeconds()),
                "words_per_minute", String.valueOf(wordsPerMinute),
                "beats", json.writeValueAsString(beatSheet),
                "dossier", json.writeValueAsString(Map.of(
                        "claims", dossier.claims(),
                        "wowFacts", dossier.wowFacts() == null ? List.of() : dossier.wowFacts(),
                        "timeline", dossier.timeline() == null ? List.of() : dossier.timeline())));
        Outline draft = llm.json(context, stage(), "outline", vars, Outline.class);
        if (draft.beats() == null || draft.beats().isEmpty()) {
            throw new IllegalStateException("Outline has no beats");
        }
        if (blueprint) {
            draft = followBlueprint(draft, ideas, context.run().targetSeconds());
        } else {
            draft.beats().stream().filter(beat -> !format.hasBeat(beat.beat()))
                    .forEach(beat -> log.warn("Outline beat '{}' is not in format {}", beat.beat(), format.id()));
        }
        context.files().write(Stage.OUTLINE, "outline.json", normalize(draft, context.run().targetSeconds()));
    }

    /** The inspiration's parts as a beat sheet: part_1, part_2... with their titles and shares. */
    private static List<Map<String, Object>> blueprintBeats(InspirationIdeas ideas) {
        List<Map<String, Object>> beats = new java.util.ArrayList<>();
        for (int i = 0; i < ideas.blueprint().size(); i++) {
            InspirationIdeas.Part part = ideas.blueprint().get(i);
            beats.add(Map.of("id", "part_" + (i + 1), "purpose", part.title() == null ? "" : part.title(),
                    "share", part.share() == null ? 1.0 / ideas.blueprint().size() : part.share()));
        }
        return beats;
    }

    /**
     * One beat per inspiration part, in their order, whatever the model returned: its chapter titles, summaries
     * and claim ids are kept where it followed the parts, and the part's own points fill any gap.
     */
    static Outline followBlueprint(Outline draft, InspirationIdeas ideas, int targetSeconds) {
        Map<String, Outline.Beat> byId = new java.util.HashMap<>();
        draft.beats().forEach(beat -> byId.putIfAbsent(beat.beat(), beat));
        List<Outline.Beat> beats = new java.util.ArrayList<>();
        int parts = ideas.blueprint().size();
        for (int i = 0; i < parts; i++) {
            InspirationIdeas.Part part = ideas.blueprint().get(i);
            String id = "part_" + (i + 1);
            Outline.Beat model = byId.get(id);
            double share = part.share() == null || part.share() <= 0 ? 1.0 / parts : part.share();
            int seconds = (int) Math.round(share * targetSeconds);
            List<String> points = new java.util.ArrayList<>(part.points() == null ? List.of() : part.points());
            if (model != null && model.points() != null) {
                model.points().stream().filter(p -> !points.contains(p)).forEach(points::add);
            }
            String title = model != null && model.chapterTitle() != null ? model.chapterTitle()
                    : i == 0 ? part.title() : null;
            beats.add(new Outline.Beat(id, title, seconds, 0,
                    model != null && model.summary() != null ? model.summary() : part.title(), points,
                    model == null ? null : model.rehook(), model == null ? List.of() : model.claimIds()));
        }
        long followed = draft.beats().stream().filter(b -> byId.containsKey(b.beat()) && b.beat().startsWith("part_"))
                .count();
        if (followed < parts) {
            log.info("Outline: rebuilt from the inspiration's {} parts (model followed {})", parts, followed);
        }
        return new Outline(targetSeconds, beats);
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
