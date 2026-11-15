package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.inspiration.MainVideoReader;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.TopicBrief;
import com.storyforge.orchestrator.model.VideoTranscript;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import com.storyforge.orchestrator.voice.VoiceCalibration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final StoryForgeProperties properties;
    private final VoiceCalibration calibration;
    private final MainVideoReader mainVideos;

    /** The main video in stretches of this many seconds, each with what was said. */
    static final int TIMELINE_CHUNK = 30;
    private static final int CHUNK_WORDS = 60;
    /** A stretch YouTube's "most replayed" curve rates at least this high is what viewers came back for. */
    static final double REPLAYED = 0.5;

    public OutlineStage(LlmRouter llm, FormatRegistry formats, JsonMapper json, StoryForgeProperties properties,
                        VoiceCalibration calibration, MainVideoReader mainVideos) {
        this.mainVideos = mainVideos;
        this.llm = llm;
        this.formats = formats;
        this.json = json;
        this.properties = properties;
        this.calibration = calibration;
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
        // The narrator's own measured speed (the critique uses the same), so a 10-minute plan is 10 minutes of voice.
        String voice = context.run().voice() != null ? context.run().voice() : properties.narration().voice();
        int wordsPerMinute = calibration.wordsPerMinute(voice);
        // With a main video that has its timings, the outline is a story spine built from it (what it says when, what
        // viewers rewatched); the model picks the moments that fit our time instead of keeping every point.
        Optional<MainVideoReader.MainVideo> main = blueprint ? mainVideos.read(context.run().inspirationId())
                : Optional.empty();
        boolean spine = main.isPresent() && main.get().transcript().timed();
        Map<String, String> vars = new HashMap<>();
        vars.put("blueprint", blueprint ? json.writeValueAsString(ideas.blueprint()) : "[]");
        vars.put("topic", brief.title());
        vars.put("angle", brief.angle());
        vars.put("core_promise", brief.corePromise());
        vars.put("target_seconds", String.valueOf(context.run().targetSeconds()));
        vars.put("words_per_minute", String.valueOf(wordsPerMinute));
        vars.put("total_words", String.valueOf(Math.round(context.run().targetSeconds() * wordsPerMinute / 60.0
                * SPEECH_SHARE)));
        vars.put("beats", json.writeValueAsString(beatSheet));
        vars.put("story", ideas != null && ideas.story() != null ? ideas.story() : "(none)");
        vars.put("timeline", spine ? timeline(main.get().transcript()) : "(none)");
        vars.put("extras", ideas != null && ideas.extras() != null && !ideas.extras().isEmpty()
                ? String.join("\n", ideas.extras()) : "(none)");
        vars.put("dossier", json.writeValueAsString(Map.of(
                "claims", dossier.claims(),
                "wowFacts", dossier.wowFacts() == null ? List.of() : dossier.wowFacts(),
                "timeline", dossier.timeline() == null ? List.of() : dossier.timeline())));
        Outline draft = llm.json(context, stage(), "outline", vars, Outline.class);
        if (draft.beats() == null || draft.beats().isEmpty()) {
            throw new IllegalStateException("Outline has no beats");
        }
        if (spine) {
            log.info("Run {}: story spine of {} beats from the main video{}", context.run().id(), draft.beats().size(),
                    draft.spine() != null && draft.spine().hooks() != null ? ", " + draft.spine().hooks().size()
                            + " hook options" : "");
        } else if (blueprint) {
            draft = followBlueprint(draft, ideas, context.run().targetSeconds());
        } else {
            draft.beats().stream().filter(beat -> !format.hasBeat(beat.beat()))
                    .forEach(beat -> log.warn("Outline beat '{}' is not in format {}", beat.beat(), format.id()));
        }
        context.files().write(Stage.OUTLINE, "outline.json", normalize(draft, context.run().targetSeconds(),
                wordsPerMinute));
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

    /**
     * The main video for the story editor: one line per TIMELINE_CHUNK seconds with what was said (any language,
     * shortened), and a star where YouTube's "most replayed" curve shows viewers came back.
     */
    static String timeline(VideoTranscript main) {
        List<VideoTranscript.Line> lines = main.segments();
        double end = lines.stream().mapToDouble(VideoTranscript.Line::end).max().orElse(0);
        StringBuilder out = new StringBuilder();
        for (double from = 0; from < end; from += TIMELINE_CHUNK) {
            double a = from;
            double b = from + TIMELINE_CHUNK;
            String said = String.join(" ", lines.stream().filter(l -> l.start() >= a && l.start() < b)
                    .map(VideoTranscript.Line::text).toList()).strip();
            if (said.isEmpty()) {
                continue;
            }
            String[] words = said.split("\\s+");
            if (words.length > CHUNK_WORDS) {
                said = String.join(" ", java.util.Arrays.copyOf(words, CHUNK_WORDS)) + " ...";
            }
            boolean replayed = main.heatmap() != null && main.heatmap().stream()
                    .anyMatch(h -> h.value() >= REPLAYED && h.start() < b && h.end() > a);
            out.append(String.format(Locale.ROOT, "[%d:%02d]%s %s%n", (int) a / 60, (int) a % 60,
                    replayed ? " ★ most replayed" : "", said));
        }
        return out.toString().strip();
    }

    static Outline normalize(Outline draft, int targetSeconds, int wordsPerMinute) {
        double total = draft.beats().stream().mapToInt(beat -> Math.max(1, beat.targetSeconds())).sum();
        List<Outline.Beat> beats = draft.beats().stream().map(beat -> {
            int seconds = (int) Math.round(Math.max(1, beat.targetSeconds()) / total * targetSeconds);
            return beat.withBudget(seconds, (int) Math.round(seconds * wordsPerMinute / 60.0 * SPEECH_SHARE));
        }).toList();
        return new Outline(targetSeconds, beats, draft.spine());
    }
}
