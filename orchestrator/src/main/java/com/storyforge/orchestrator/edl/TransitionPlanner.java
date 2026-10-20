package com.storyforge.orchestrator.edl;

import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks a transition (and its sound) for every cut. Deterministic, so the audio mix and the render agree:
 * chapters get a whip or zoom with a swish, tense scene changes a flash with an impact, kinetic text a soft
 * zoom, and roughly every 20 seconds a scene change gets a whip so the pace never sags. Everything else crossfades.
 */
public final class TransitionPlanner {

    public static final String FADE = "fade";
    public static final String WHIP = "whip";
    public static final String ZOOM = "zoom";
    public static final String FLASH = "flash";

    /** sfx: media-svc preset; lead: seconds the sound starts before the cut so its peak lands on it. */
    public record Transition(String shotId, double at, String kind, String sfx, double gainDb, double lead) {
    }

    private static final double CHAPTER_TOLERANCE = 0.35;
    private static final double MIN_ENERGETIC_GAP = 8;
    private static final double FLASH_GAP = 12;
    private static final double PACE_GAP = 20;
    private static final Set<String> TENSE = Set.of("tense", "dread", "urgent", "shock", "fear", "danger", "dark");

    private TransitionPlanner() {
    }

    public static List<Transition> plan(ShotList shots, List<Scene> scenes, List<EdlChapter> chapters) {
        Map<String, Scene> sceneById = scenes.stream().collect(Collectors.toMap(Scene::id, Function.identity(),
                (a, b) -> a));
        List<Transition> out = new ArrayList<>();
        List<Shot> list = shots.shots();
        double lastEnergetic = -100;
        int chapterCount = 0;
        for (int i = 1; i < list.size(); i++) {
            Shot shot = list.get(i);
            Shot previous = list.get(i - 1);
            double at = shot.start();
            boolean chapter = chapters.stream().skip(1)
                    .anyMatch(c -> Math.abs(c.start() - at) <= CHAPTER_TOLERANCE);
            boolean sceneChange = !shot.sceneId().equals(previous.sceneId());
            Transition transition;
            if (chapter) {
                boolean whip = chapterCount++ % 2 == 0;
                transition = whip ? new Transition(shot.id(), at, WHIP, "whip", -9, 0.23)
                        : new Transition(shot.id(), at, ZOOM, "zoom_in", -9, 0.68);
            } else if (shot.graphic() && at - lastEnergetic >= MIN_ENERGETIC_GAP) {
                transition = new Transition(shot.id(), at, ZOOM, "swoosh_soft", -15, 0.33);
            } else if (sceneChange && tense(sceneById.get(shot.sceneId())) && at - lastEnergetic >= FLASH_GAP) {
                transition = new Transition(shot.id(), at, FLASH, "flash_hit", -11, 0.0);
            } else if (sceneChange && at - lastEnergetic >= PACE_GAP) {
                transition = new Transition(shot.id(), at, WHIP, "whip", -12, 0.23);
            } else {
                transition = new Transition(shot.id(), at, FADE, null, 0, 0);
            }
            if (transition.sfx() != null) {
                lastEnergetic = at;
            }
            out.add(transition);
        }
        return out;
    }

    public static Map<String, String> kinds(List<Transition> transitions) {
        Map<String, String> kinds = new HashMap<>();
        transitions.forEach(t -> kinds.put(t.shotId(), t.kind()));
        return kinds;
    }

    private static boolean tense(Scene scene) {
        if (scene == null) {
            return false;
        }
        String mood = (nullToEmpty(scene.tone()) + " " + nullToEmpty(scene.emotion())).toLowerCase(Locale.ROOT);
        return TENSE.stream().anyMatch(mood::contains);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
