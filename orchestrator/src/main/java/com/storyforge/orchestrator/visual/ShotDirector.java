package com.storyforge.orchestrator.visual;

import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Overlay;
import com.storyforge.orchestrator.model.RealMedia;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotDirection;
import com.storyforge.orchestrator.model.ShotDirection.OverlayDraft;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.model.VisualBible;
import com.storyforge.orchestrator.provider.llm.LlmException;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns the rule-based shot plan into directed shots: a visual bible keeps people and places consistent, and every
 * image gets its own prompt from the words spoken during it. Overlays are only kept when the narration says them.
 * Any LLM failure falls back to the rule-based prompts, so a run never stops here.
 */
@Component
public class ShotDirector {

    public record Result(ShotList shots, List<Overlay> overlays, VisualBible bible, Map<String, RealMedia> realMedia) {
    }

    /** Directed prompts of earlier runs of this video, keyed by scene, shot index and scene text. */
    public record Memory(int salt, VisualBible bible, Map<String, String> prompts, List<OverlayDraft> overlays,
                         Map<String, RealMedia> real) {
    }

    private static final double MAX_REAL_SHARE = 0.5;

    public static final String MEMORY = "prompt_memory.json";

    private static final Logger log = LoggerFactory.getLogger(ShotDirector.class);
    private static final double MIN_OVERLAY_GAP_SECONDS = 20;
    private static final double SECONDS_PER_OVERLAY = 40;

    private final LlmRouter llm;
    private final JsonMapper json;
    private final boolean enabled;
    private final int chunkSize;

    public ShotDirector(LlmRouter llm, JsonMapper json,
                        @Value("${storyforge.director.enabled:true}") boolean enabled,
                        @Value("${storyforge.director.chunk-size:40}") int chunkSize) {
        this.llm = llm;
        this.json = json;
        this.enabled = enabled;
        this.chunkSize = chunkSize;
    }

    public Result direct(StageContext context, String topic, List<Scene> scenes, Alignment alignment, ShotList plan,
                         FormatDefinition.Style style) {
        if (!enabled) {
            return new Result(plan, List.of(), VisualBible.empty(), Map.of());
        }
        Memory memory = context.files().readRoot(MEMORY, Memory.class)
                .filter(m -> m.salt() == context.run().cacheSalt() && m.bible() != null).orElse(null);
        VisualBible bible;
        if (memory != null) {
            bible = memory.bible();
        } else {
            try {
                bible = llm.json(context, Stage.VISUAL_PLAN, "direct_bible", Map.of("topic", topic,
                        "art_direction", style.artDirection(), "narration", narration(scenes)), VisualBible.class);
            } catch (LlmException e) {
                log.warn("Run {}: visual bible failed, keeping rule-based prompts: {}", context.run().id(),
                        e.getMessage());
                return new Result(plan, List.of(), VisualBible.empty(), Map.of());
            }
        }
        Map<String, Scene> sceneById = scenes.stream().collect(Collectors.toMap(Scene::id, Function.identity()));
        Map<String, String> keys = memoryKeys(plan, sceneById);
        Map<String, String> remembered = memory == null ? Map.of() : memory.prompts();
        Map<String, String> prompts = new HashMap<>();
        List<OverlayDraft> drafts = new ArrayList<>();
        Set<String> rememberedIds = new HashSet<>();
        Map<String, RealMedia> rememberedReal = memory == null || memory.real() == null ? Map.of() : memory.real();
        Map<String, RealMedia> real = new HashMap<>();
        for (Shot shot : plan.shots()) {
            String known = remembered.get(keys.get(shot.id()));
            if (!shot.graphic() && known != null) {
                prompts.put(shot.id(), known);
                rememberedIds.add(shot.id());
                if (rememberedReal.containsKey(keys.get(shot.id()))) {
                    real.put(shot.id(), rememberedReal.get(keys.get(shot.id())));
                }
            }
        }
        if (memory != null && memory.overlays() != null) {
            drafts.addAll(memory.overlays().stream().filter(d -> rememberedIds.contains(d.shot())).toList());
        }
        List<Shot> images = plan.shots().stream().filter(shot -> !shot.graphic())
                .filter(shot -> !rememberedIds.contains(shot.id())).toList();
        String bibleJson = json.writeValueAsString(bible);
        for (int from = 0; from < images.size(); from += chunkSize) {
            List<Shot> chunk = images.subList(from, Math.min(images.size(), from + chunkSize));
            String lines = chunk.stream().map(shot -> String.join(" | ", shot.id(), shot.type(),
                            "scene " + shot.sceneId(), "\"" + spoken(alignment, shot) + "\"",
                            nullToEmpty(sceneById.get(shot.sceneId()).visual())))
                    .collect(Collectors.joining("\n"));
            try {
                ShotDirection direction = llm.json(context, Stage.VISUAL_PLAN, "direct_shots",
                        Map.of("bible", bibleJson, "shots", lines), ShotDirection.class);
                if (direction.shots() != null) {
                    direction.shots().stream().filter(p -> p.id() != null && p.prompt() != null
                            && !p.prompt().isBlank()).forEach(p -> {
                                prompts.put(p.id(), p.prompt().strip());
                                if (p.real() != null && p.real().valid()) {
                                    real.put(p.id(), new RealMedia(p.real().kind(), p.real().query().strip()));
                                }
                            });
                }
                if (direction.overlays() != null) {
                    drafts.addAll(direction.overlays());
                }
            } catch (LlmException e) {
                log.warn("Run {}: shot direction failed for {} shots from {}, keeping rule-based prompts: {}",
                        context.run().id(), chunk.size(), chunk.get(0).id(), e.getMessage());
            }
        }
        Map<String, String> nextMemory = new HashMap<>(remembered);
        prompts.forEach((id, prompt) -> nextMemory.put(keys.get(id), prompt));
        Map<String, RealMedia> nextReal = new HashMap<>(rememberedReal);
        real.forEach((id, media) -> nextReal.put(keys.get(id), media));
        context.files().writeRoot(MEMORY, new Memory(context.run().cacheSalt(), bible, nextMemory, drafts, nextReal));
        capRealShare(real, plan);
        List<Shot> directed = plan.shots().stream().map(shot -> prompts.containsKey(shot.id())
                ? withPrompt(shot, style.promptPrefix() + ", " + prompts.get(shot.id()))
                : shot).toList();
        log.info("Run {}: directed {} images ({} remembered), {} overlay draft(s)", context.run().id(),
                prompts.size(), rememberedIds.size(), drafts.size());
        ShotList shots = new ShotList(plan.durationSec(), directed);
        return new Result(shots, overlays(drafts, shots, scenes), bible, real);
    }

    /** Keeps at most half of the pictures real, spread evenly, so the illustrated style still carries the film. */
    static void capRealShare(Map<String, RealMedia> real, ShotList plan) {
        long pictures = plan.shots().stream().filter(s -> !s.graphic()).count();
        int max = (int) Math.floor(pictures * MAX_REAL_SHARE);
        if (real.size() <= max) {
            return;
        }
        List<String> ordered = plan.shots().stream().map(Shot::id).filter(real::containsKey).toList();
        Set<String> keep = new HashSet<>();
        for (int i = 0; i < max; i++) {
            keep.add(ordered.get((int) ((long) i * ordered.size() / max)));
        }
        real.keySet().retainAll(keep);
    }

    /** Stable key per shot: same scene, same position in the scene and same scene text means the same picture. */
    static Map<String, String> memoryKeys(ShotList plan, Map<String, Scene> sceneById) {
        Map<String, Integer> perScene = new HashMap<>();
        Map<String, String> keys = new HashMap<>();
        for (Shot shot : plan.shots()) {
            int n = perScene.merge(shot.sceneId(), 1, Integer::sum);
            String text = nullToEmpty(sceneById.get(shot.sceneId()).narration());
            keys.put(shot.id(), shot.sceneId() + "#" + n + "@" + Integer.toHexString(text.hashCode()));
        }
        return keys;
    }

    static List<Overlay> overlays(List<OverlayDraft> drafts, ShotList shots, List<Scene> scenes) {
        String corpus = " " + normalize(scenes.stream().map(Scene::narration).filter(n -> n != null)
                .collect(Collectors.joining(" "))) + " ";
        Map<String, Shot> byId = shots.shots().stream().collect(Collectors.toMap(Shot::id, Function.identity()));
        int max = Math.max(1, (int) (shots.durationSec() / SECONDS_PER_OVERLAY));
        List<Overlay> kept = new ArrayList<>();
        double lastStart = Double.NEGATIVE_INFINITY;
        List<OverlayDraft> ordered = drafts.stream().filter(d -> d.shot() != null && byId.containsKey(d.shot()))
                .sorted(Comparator.comparingDouble(d -> byId.get(d.shot()).start())).toList();
        for (OverlayDraft draft : ordered) {
            Shot shot = byId.get(draft.shot());
            String kind = draft.kind() == null ? "" : draft.kind().toLowerCase(Locale.ROOT).strip();
            String title = clean(draft.title());
            String subtitle = clean(draft.subtitle());
            if (shot.graphic() || title.isEmpty() || kept.size() >= max) {
                continue;
            }
            double seconds;
            switch (kind) {
                case Overlay.LOCATION -> {
                    if (!said(corpus, title.split(",")[0])) {
                        continue;
                    }
                    seconds = 4.5;
                }
                case Overlay.QUOTE -> {
                    int words = title.split("\\s+").length;
                    if (words < 4 || words > 35 || !said(corpus, title)) {
                        continue;
                    }
                    seconds = Math.max(4, Math.min(9, shot.duration()));
                }
                case Overlay.YEAR -> {
                    if (!title.matches("\\d{3,4}s?") || !said(corpus, title)) {
                        continue;
                    }
                    subtitle = "";
                    seconds = 3;
                }
                default -> {
                    continue;
                }
            }
            double start = shot.start() + 0.3;
            if (start - lastStart < MIN_OVERLAY_GAP_SECONDS) {
                continue;
            }
            if (!subtitle.isEmpty() && !supported(corpus, subtitle)) {
                subtitle = "";
            }
            double end = Math.min(shots.durationSec(), start + seconds);
            kept.add(new Overlay(kind, shot.id(), round(start), round(end), title, subtitle));
            lastStart = start;
        }
        return kept;
    }

    /** Every number and every longer word of the text must appear in the narration. */
    private static boolean supported(String corpus, String text) {
        for (String token : normalize(text).split(" ")) {
            boolean digits = token.chars().anyMatch(Character::isDigit);
            if ((digits || token.length() >= 4) && !corpus.contains(" " + token + " ")) {
                return false;
            }
        }
        return true;
    }

    private static boolean said(String corpus, String text) {
        String needle = normalize(text);
        return !needle.isEmpty() && corpus.contains(" " + needle + " ");
    }

    static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static String clean(String text) {
        return text == null ? "" : text.strip().replaceAll("^[\"“”'‘’]+|[\"“”'‘’]+$", "").strip();
    }

    private static String spoken(Alignment alignment, Shot shot) {
        return alignment.words().stream()
                .filter(w -> (w.start() + w.end()) / 2 >= shot.start() && (w.start() + w.end()) / 2 < shot.end())
                .map(Alignment.Word::text).collect(Collectors.joining(" ")).replace("\"", "'");
    }

    private static String narration(List<Scene> scenes) {
        return scenes.stream().map(scene -> "[" + scene.id() + "] " + nullToEmpty(scene.narration()))
                .collect(Collectors.joining("\n"));
    }

    static Shot withPrompt(Shot shot, String prompt) {
        return new Shot(shot.id(), shot.sceneId(), shot.type(), shot.start(), shot.end(), prompt, shot.motion(),
                shot.text(), shot.seed());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }
}
