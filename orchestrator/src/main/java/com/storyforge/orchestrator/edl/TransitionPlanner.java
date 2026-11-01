package com.storyforge.orchestrator.edl;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Edits like a YouTube editor, deterministically so the audio mix and the render agree. Hard cuts by default; every
 * cut that matters gets a transition with its own sound:
 * <ul>
 *   <li>chapters: a film burn or a zoom-through with a whoosh,</li>
 *   <li>a reveal (the narrator pauses inside a scene): a camera-shake impact with a boom, or for the longest pauses
 *       a dip to black with a sub drop,</li>
 *   <li>tense scene changes: a glitch or a white flash with a hit,</li>
 *   <li>kinetic text: a soft zoom; every ~14 seconds a scene change gets a whip so the pace never sags,</li>
 *   <li>reflective scene changes: a slow crossfade.</li>
 * </ul>
 * Inside long shots a punch-in (same picture, tighter crop, a quiet shutter click) keeps the picture changing every
 * two to three seconds without spending more of the free image quota.
 */
public final class TransitionPlanner {

    public static final String CUT = "cut";
    public static final String FADE = "fade";
    public static final String WHIP = "whip";
    public static final String ZOOM = "zoom";
    public static final String FLASH = "flash";
    public static final String GLITCH = "glitch";
    public static final String BURN = "burn";
    public static final String IMPACT = "impact";
    public static final String DIP = "dip";

    /** sfx: media-svc preset; lead: seconds the sound starts before the cut so its peak lands on it. */
    public record Transition(String shotId, double at, String kind, String sfx, double gainDb, double lead) {
    }

    /** A punch-in inside a shot: from {@code at} to the shot's end the picture is cropped around (x, y), 0..1. */
    public record Punch(String shotId, double at, double x, double y, double zoom, String sfx, double gainDb) {
    }

    private static final double CHAPTER_TOLERANCE = 0.35;
    private static final double MIN_ENERGETIC_GAP = 6;
    private static final double PACE_GAP = 14;
    /** Normal sentence gaps are ~0.4-0.6 s; only a deliberate pause before a reveal is longer. */
    private static final double REVEAL_PAUSE = 0.7;
    /** A shake-and-boom is special: at most one every 40 s. */
    private static final double IMPACT_GAP = 40;
    private static final double DIP_PAUSE = 0.9;
    private static final int MAX_DIPS = 2;
    private static final double PUNCH_MIN_SHOT = 3.2;
    private static final double PUNCH_CLEAR = 1.2;
    private static final double PUNCH_GAIN_DB = -24;
    private static final Set<String> TENSE = Set.of("tense", "dread", "urgent", "shock", "fear", "danger", "dark",
            "ominous", "angry");
    private static final Set<String> CALM = Set.of("reflective", "calm", "sad", "melancholic", "tender", "quiet",
            "somber", "wistful");

    private TransitionPlanner() {
    }

    public static List<Transition> plan(ShotList shots, List<Scene> scenes, List<EdlChapter> chapters) {
        return plan(shots, scenes, chapters, null);
    }

    public static List<Transition> plan(ShotList shots, List<Scene> scenes, List<EdlChapter> chapters,
                                        Alignment alignment) {
        Map<String, Scene> sceneById = scenes.stream().collect(Collectors.toMap(Scene::id, Function.identity(),
                (a, b) -> a));
        List<Word> words = alignment == null ? List.of()
                : alignment.words().stream().sorted(Comparator.comparingDouble(Word::start)).toList();
        List<Transition> out = new ArrayList<>();
        List<Shot> list = shots.shots();
        double lastEnergetic = -100;
        int chapterCount = 0;
        int tenseCount = 0;
        int paceCount = 0;
        int dips = 0;
        double lastImpact = -100;
        for (int i = 1; i < list.size(); i++) {
            Shot shot = list.get(i);
            Shot previous = list.get(i - 1);
            double at = shot.start();
            boolean chapter = chapters.stream().skip(1)
                    .anyMatch(c -> Math.abs(c.start() - at) <= CHAPTER_TOLERANCE);
            boolean sceneChange = !shot.sceneId().equals(previous.sceneId());
            boolean spaced = at - lastEnergetic >= MIN_ENERGETIC_GAP;
            double pause = sceneChange ? 0 : pauseAt(words, at);
            Scene scene = sceneById.get(shot.sceneId());
            Transition transition;
            if (chapter) {
                transition = chapterCount++ % 2 == 0 ? new Transition(shot.id(), at, BURN, "film_burn", -8, 0.45)
                        : new Transition(shot.id(), at, ZOOM, "zoom_in", -9, 0.68);
            } else if (pause >= REVEAL_PAUSE && spaced && !shot.graphic()
                    && (pause >= DIP_PAUSE && dips < MAX_DIPS || at - lastImpact >= IMPACT_GAP)) {
                boolean dip = pause >= DIP_PAUSE && dips < MAX_DIPS;
                if (dip) {
                    dips++;
                } else {
                    lastImpact = at;
                }
                transition = dip ? new Transition(shot.id(), at, DIP, "sub_drop", -11, 0.0)
                        : new Transition(shot.id(), at, IMPACT, "boom", -10, 0.0);
            } else if (shot.graphic() && spaced) {
                transition = new Transition(shot.id(), at, ZOOM, "swoosh_soft", -15, 0.33);
            } else if (sceneChange && matches(scene, TENSE) && spaced) {
                transition = tenseCount++ % 2 == 0 ? new Transition(shot.id(), at, GLITCH, "glitch", -14, 0.05)
                        : new Transition(shot.id(), at, FLASH, "flash_hit", -11, 0.0);
            } else if (sceneChange && at - lastEnergetic >= PACE_GAP) {
                transition = paceCount++ % 2 == 0 ? new Transition(shot.id(), at, WHIP, "whip", -12, 0.23)
                        : new Transition(shot.id(), at, ZOOM, "whoosh", -13, 0.45);
            } else if (sceneChange && matches(scene, CALM)) {
                transition = new Transition(shot.id(), at, FADE, null, 0, 0);
            } else {
                transition = new Transition(shot.id(), at, CUT, null, 0, 0);
            }
            if (transition.sfx() != null) {
                lastEnergetic = at;
            }
            out.add(transition);
        }
        return out;
    }

    /**
     * Punch-ins for shots long enough to hold two framings. {@code excluded}: shots that must stay untouched
     * (animated maps, text cards). A punch next to a transition keeps its picture change but stays silent.
     */
    public static List<Punch> punches(ShotList shots, Set<String> excluded, List<Transition> transitions) {
        List<Double> loud = transitions.stream().filter(t -> t.sfx() != null).map(Transition::at).toList();
        List<Punch> out = new ArrayList<>();
        for (Shot shot : shots.shots()) {
            if (shot.graphic() || excluded.contains(shot.id()) || shot.duration() < PUNCH_MIN_SHOT) {
                continue;
            }
            double at = round(shot.start() + shot.duration() * 0.52);
            int hash = Math.floorMod(shot.id().hashCode() * 31 + (int) shot.seed(), 9);
            double x = new double[] {0.32, 0.5, 0.68}[hash % 3];
            double y = new double[] {0.38, 0.5, 0.6}[hash / 3];
            double zoom = 1.22 + (hash % 4) * 0.04;
            boolean quiet = loud.stream().anyMatch(t -> Math.abs(t - at) < PUNCH_CLEAR);
            out.add(new Punch(shot.id(), at, x, y, zoom, quiet ? null : "shutter", PUNCH_GAIN_DB));
        }
        return out;
    }

    public static Map<String, String> kinds(List<Transition> transitions) {
        Map<String, String> kinds = new HashMap<>();
        transitions.forEach(t -> kinds.put(t.shotId(), t.kind()));
        return kinds;
    }

    /** Silence in the narration around a cut: a [PAUSE] the writer put before a reveal. */
    static double pauseAt(List<Word> words, double at) {
        Word before = null;
        Word after = null;
        for (Word word : words) {
            if (word.end() <= at + 0.05) {
                before = word;
            } else if (word.start() >= at - 0.05) {
                after = word;
                break;
            } else {
                return 0;
            }
        }
        return before == null || after == null ? 0 : after.start() - before.end();
    }

    private static boolean matches(Scene scene, Set<String> moods) {
        if (scene == null) {
            return false;
        }
        String mood = (nullToEmpty(scene.tone()) + " " + nullToEmpty(scene.emotion())).toLowerCase(Locale.ROOT);
        return moods.stream().anyMatch(mood::contains);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }
}
