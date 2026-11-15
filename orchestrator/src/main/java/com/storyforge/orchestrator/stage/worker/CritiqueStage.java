package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Critique;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.script.NarrationStyle;
import com.storyforge.orchestrator.script.SfmParseException;
import com.storyforge.orchestrator.script.SfmParser;
import com.storyforge.orchestrator.script.SpokenCheck;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import com.storyforge.orchestrator.voice.VoiceCalibration;
import java.util.HashMap;
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

    private static final double LENGTH_MARGIN = 0.8;
    // Scripts now have few pauses (one per scene at most), so almost all the runtime is speech.
    private static final double SPEECH_SHARE = 0.97;

    private final LlmRouter llm;
    private final JsonMapper json;
    private final StoryForgeProperties properties;
    private final VoiceCalibration calibration;
    private final NarrationStyle style;

    public CritiqueStage(LlmRouter llm, JsonMapper json, StoryForgeProperties properties,
                         VoiceCalibration calibration, NarrationStyle style) {
        this.calibration = calibration;
        this.style = style;
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
        String outlineJson = json.writeValueAsString(outline.spine() == null ? outline.beats() : outline);
        String claimsJson = json.writeValueAsString(dossier.claims());
        String markup = context.files().readText(Stage.SCRIPT, "script.sfm.md");

        String voice = context.run().voice() != null ? context.run().voice() : properties.narration().voice();
        int wordsPerMinute = calibration.wordsPerMinute(voice);
        int targetSeconds = context.run().targetSeconds();
        double tolerance = properties.qa().durationTolerance() * LENGTH_MARGIN;
        int targetWords = (int) Math.round(targetSeconds * wordsPerMinute / 60.0 * SPEECH_SHARE);
        Version best = null;
        String story = style.vars(context).get("story");
        int maxRewrites = properties.llm().maxRewrites();
        for (int round = 0; round <= maxRewrites; round++) {
            Critique critique = llm.json(context, stage(), "critique",
                            Map.of("script", markup, "outline", outlineJson, "claims", claimsJson, "story", story),
                            Critique.class)
                    .evaluated(properties.qa().criticAverage(), properties.qa().criticHook(), round);
            List<Scene> scenes = SfmParser.parse(markup);
            critique = critique.withLength(wordCount(scenes), estimatedSeconds(scenes, wordsPerMinute), targetSeconds,
                    tolerance, wordsPerMinute);
            SpokenCheck.Result spoken = SpokenCheck.check(scenes);
            critique = critique.withSpoken(spoken.flags(), spoken.hasMajor());
            log.info("Run {} critique round {}: spoken check {} ({} flags)", context.run().id(), round, spoken.stats(),
                    spoken.flags().size());
            Version version = new Version(markup, scenes, critique);
            if (best == null || better(critique, best.critique())) {
                best = version;
            }
            if (critique.passed() || round == maxRewrites) {
                break;
            }
            log.info("Run {} critique round {} below threshold (avg {}, hook {}), rewriting", context.run().id(),
                    round, critique.average(), critique.hook());
            Map<String, String> vars = new HashMap<>(style.vars(context));
            vars.putAll(Map.of(
                    "script", markup,
                    "critique", json.writeValueAsString(critique),
                    "outline", outlineJson,
                    "claims", claimsJson,
                    "total_words", String.valueOf(targetWords)));
            String rewritten = llm.text(context, Stage.SCRIPT, "rewrite", vars);
            try {
                SfmParser.parse(rewritten);
                markup = rewritten;
            } catch (SfmParseException e) {
                log.warn("Rewrite produced invalid markup, keeping previous version: {}", e.getMessage());
            }
        }
        if (!best.critique().lengthOk()) {
            best = trimmed(context, best, targetSeconds, tolerance, wordsPerMinute);
        }
        best = endingCleaned(context, best, targetSeconds, tolerance, wordsPerMinute);
        context.files().write(Stage.CRITIQUE, "critique.json", best.critique());
        context.files().writeText(Stage.CRITIQUE, "script.sfm.md", best.markup());
        context.files().write(Stage.CRITIQUE, "scenes.json", best.scenes());
    }

    /** One ask at the end, whatever the rewrites did (see SpokenCheck.cleanEnding). */
    private Version endingCleaned(StageContext context, Version best, int targetSeconds, double tolerance,
                                  int wordsPerMinute) {
        String cleaned = SpokenCheck.cleanEnding(best.markup());
        if (cleaned.equals(best.markup())) {
            return best;
        }
        try {
            List<Scene> scenes = SfmParser.parse(cleaned);
            log.info("Run {}: extra asks removed from the last scene", context.run().id());
            return new Version(cleaned, scenes, best.critique().withLength(wordCount(scenes),
                    estimatedSeconds(scenes, wordsPerMinute), targetSeconds, tolerance, wordsPerMinute));
        } catch (SfmParseException e) {
            log.warn("Ending clean-up produced invalid markup, kept the script as it was: {}", e.getMessage());
            return best;
        }
    }

    private Version trimmed(StageContext context, Version best, int targetSeconds, double tolerance,
                            int wordsPerMinute) {
        int words = wordCount(best.scenes());
        int goal = (int) Math.round(words * targetSeconds / Math.max(1.0, best.critique().estimatedSeconds()));
        String markup = llm.text(context, Stage.CRITIQUE, "trim", Map.of(
                "script", best.markup(),
                "words", String.valueOf(words),
                "target_words", String.valueOf(goal),
                "direction", goal < words ? "cut" : "add",
                "delta", String.valueOf(Math.abs(words - goal))));
        try {
            List<Scene> scenes = SfmParser.parse(markup);
            Critique critique = best.critique().withLength(wordCount(scenes), estimatedSeconds(scenes, wordsPerMinute),
                    targetSeconds, tolerance, wordsPerMinute);
            double before = Math.abs(best.critique().estimatedSeconds() - targetSeconds);
            double after = Math.abs(critique.estimatedSeconds() - targetSeconds);
            if (after < before) {
                log.info("Run {} trimmed script from {} to {} words", context.run().id(), words, critique.words());
                return new Version(markup, scenes, critique);
            }
        } catch (SfmParseException e) {
            log.warn("Trim produced invalid markup, keeping previous version: {}", e.getMessage());
        }
        return best;
    }

    static double estimatedSeconds(List<Scene> scenes, int wordsPerMinute) {
        double total = 0;
        for (Scene scene : scenes) {
            double speed = scene.speed() > 0 ? scene.speed() : 1.0;
            total += scene.words().size() * 60.0 / (wordsPerMinute * speed);
            total += (scene.silenceBeforeMs() + scene.silenceAfterMs()) / 1000.0;
            total += scene.segments().stream().filter(Scene.Segment::silent).mapToInt(Scene.Segment::pauseMs).sum()
                    / 1000.0;
        }
        return total;
    }

    static int wordCount(List<Scene> scenes) {
        return scenes.stream().mapToInt(scene -> scene.words().size()).sum();
    }

    static boolean better(Critique candidate, Critique current) {
        if (candidate.lengthOk() != current.lengthOk()) {
            return candidate.lengthOk();
        }
        return candidate.average() > current.average();
    }
}
