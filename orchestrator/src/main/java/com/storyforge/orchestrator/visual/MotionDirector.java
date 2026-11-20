package com.storyforge.orchestrator.visual;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.MotionGraphic;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.model.ShotType;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * A few text cards become animated explainers from render-svc's motion kit (versus, chain, list, stat, timeline,
 * evidence board, quote, merge), in the channel's colors. Claude picks the moments where an animation explains better
 * than film and writes the words; this class keeps only what fits the rules and puts every item on the word that
 * says it. Any failure leaves the plain text cards.
 */
@Component
public class MotionDirector {

    /** Claude's answer. cue: 1-4 words copied from the narration where the item should appear. */
    public record Answer(List<Draft> graphics) {
    }

    public record Draft(String shot, String template, String title, List<MotionGraphic.Item> items) {
    }

    public static final String MOTION = "motion.json";

    /** The film hook keeps the opening. */
    static final double NO_ANIMATION_BEFORE = 15;
    static final double MIN_GAP_SECONDS = 90;
    static final double MAX_SHARE = 0.08;
    /** An animation needs time to build; a shorter card takes over the next shots of the same scene. */
    static final double MIN_SECONDS = 5;
    static final double MAX_SECONDS = 9;
    /** An item appears just before its word, so the eye lands as the ear does. */
    static final double LEAD = 0.15;
    private static final double TAIL = 0.8;
    /** The last item (a merge's result, the last clue) stays at least this long before the animation leaves. */
    static final double READ_SECONDS = 1.6;

    private static final Logger log = LoggerFactory.getLogger(MotionDirector.class);

    private final LlmRouter llm;
    private final JsonMapper json;

    public MotionDirector(LlmRouter llm, JsonMapper json) {
        this.llm = llm;
        this.json = json;
    }

    /** 4-6 for a 10-minute video: one per two minutes, at most 6. */
    static int wanted(double durationSec) {
        return (int) Math.max(1, Math.min(6, Math.round(durationSec / 120.0)));
    }

    /**
     * A moment that may become an animation: after the hook, a text card or a film shot (the strongest moments, such
     * as the twist's payoff, are rarely where a text card happens to be); never a map.
     */
    static boolean candidate(Shot shot) {
        return shot.start() >= NO_ANIMATION_BEFORE
                && (ShotType.KINETIC_TEXT.equals(shot.type()) || !shot.graphic());
    }

    public ShotList animate(StageContext context, List<Scene> scenes, Alignment alignment, ShotList shots) {
        List<Shot> candidates = shots.shots().stream().filter(MotionDirector::candidate).toList();
        if (candidates.isEmpty()) {
            return shots;
        }
        try {
            Map<String, String> vars = new HashMap<>();
            vars.put("topic", context.run().topicTitle());
            vars.put("count", String.valueOf(wanted(shots.durationSec())));
            vars.put("spine", spine(context));
            vars.put("shots", candidates.stream().map(s -> line(s, alignment)).collect(Collectors.joining("\n")));
            Answer answer = llm.json(context, Stage.VISUAL_PLAN, "direct_motion", vars, Answer.class);
            Map<String, String> tones = scenes.stream().collect(Collectors.toMap(Scene::id,
                    s -> s.tone() == null ? "" : s.tone(), (a, b) -> a));
            ShotList animated = apply(shots, answer, alignment.words(), tones);
            List<Shot> placed = animated.shots().stream().filter(s -> s.animation() != null).toList();
            context.files().write(Stage.VISUAL_PLAN, MOTION, placed.stream().map(s -> Map.of("shot", s.id(),
                    "start", s.start(), "end", s.end(), "animation", s.animation())).toList());
            log.info("Run {}: {} animated explainer(s): {}", context.run().id(), placed.size(),
                    placed.stream().map(s -> s.animation().template() + "@" + Math.round(s.start()) + "s").toList());
            return animated;
        } catch (RuntimeException e) {
            log.warn("Run {}: animations skipped, plain text cards kept: {}", context.run().id(), e.getMessage());
            return shots;
        }
    }

    /** Validates Claude's picks, keeps the spacing rules, lengthens short cards and times every item. */
    static ShotList apply(ShotList shots, Answer answer, List<Alignment.Word> words, Map<String, String> tones) {
        if (answer == null || answer.graphics() == null || answer.graphics().isEmpty()) {
            return shots;
        }
        Map<String, Shot> byId = shots.shots().stream().collect(Collectors.toMap(Shot::id, Function.identity()));
        List<Draft> drafts = answer.graphics().stream()
                .filter(d -> d.shot() != null && byId.containsKey(d.shot()) && candidate(byId.get(d.shot()))
                        && clean(d) != null)
                .sorted(Comparator.comparingDouble(d -> byId.get(d.shot()).start())).toList();
        int max = wanted(shots.durationSec());
        double budget = shots.durationSec() * MAX_SHARE;
        List<Shot> list = new ArrayList<>(shots.shots());
        Set<String> absorbed = new HashSet<>();
        double last = Double.NEGATIVE_INFINITY;
        double used = 0;
        int kept = 0;
        for (Draft draft : drafts) {
            int index = indexOf(list, draft.shot());
            if (index < 0 || kept >= max) {
                continue;
            }
            Shot shot = list.get(index);
            if (shot.start() - last < MIN_GAP_SECONDS) {
                continue;
            }
            MotionGraphic draftGraphic = clean(draft);
            String tone = tones.get(shot.sceneId());
            int end = index;
            double until = shot.end();
            while (until - shot.start() < MIN_SECONDS && canTake(list, end + 1, shot)) {
                end++;
                until = list.get(end).end();
            }
            MotionGraphic graphic = timed(draftGraphic, shot.start(), until, words, tone);
            // The last item (a merge's result, the last clue) needs time to be read: take the next shot if we can.
            // (At the cap means it was pulled earlier than its word to fit.)
            while (lastAt(graphic) >= until - READ_SECONDS - 0.05 && canTake(list, end + 1, shot)) {
                end++;
                until = list.get(end).end();
                graphic = timed(draftGraphic, shot.start(), until, words, tone);
            }
            double length = until - shot.start();
            if (used + length > budget) {
                continue;
            }
            for (int i = index + 1; i <= end; i++) {
                absorbed.add(list.get(i).id());
            }
            // A film shot turned into an animation becomes a graphic; its title is the text card's fallback words.
            String text = shot.text() != null && !shot.text().isBlank() ? shot.text()
                    : !graphic.title().isBlank() ? graphic.title() : graphic.items().get(0).label();
            list.set(index, new Shot(shot.id(), shot.sceneId(), ShotType.KINETIC_TEXT, shot.start(), until, null,
                    "none", text, shot.seed(), graphic));
            used += length;
            last = shot.start();
            kept++;
        }
        list.removeIf(s -> absorbed.contains(s.id()));
        return new ShotList(shots.durationSec(), list);
    }

    /** The next shot can be taken over: same scene, not a graphic, and the animation stays under MAX_SECONDS. */
    private static boolean canTake(List<Shot> list, int i, Shot first) {
        if (i >= list.size()) {
            return false;
        }
        Shot next = list.get(i);
        return next.sceneId().equals(first.sceneId()) && !next.graphic() && next.end() - first.start() <= MAX_SECONDS;
    }

    private static double lastAt(MotionGraphic graphic) {
        Double at = graphic.items().get(graphic.items().size() - 1).at();
        return at == null ? 0 : at;
    }

    /** The draft cut to what the template can show, or null when it cannot be shown at all. */
    static MotionGraphic clean(Draft draft) {
        if (draft.template() == null) {
            return null;
        }
        String template = draft.template().strip().toLowerCase(Locale.ROOT);
        int[] range = MotionGraphic.TEMPLATES.get(template);
        if (range == null || draft.items() == null) {
            return null;
        }
        List<MotionGraphic.Item> items = draft.items().stream()
                .filter(i -> i != null && i.label() != null && !i.label().isBlank())
                .limit(range[1])
                .map(i -> new MotionGraphic.Item(
                        cut(i.label(), "quote".equals(template) ? MotionGraphic.MAX_QUOTE : MotionGraphic.MAX_LABEL),
                        i.detail() == null ? null : cut(i.detail(), MotionGraphic.MAX_DETAIL), i.cue(), null))
                .toList();
        if (items.size() < range[0]) {
            return null;
        }
        String title = draft.title() == null ? "" : cut(draft.title(), MotionGraphic.MAX_TITLE);
        return new MotionGraphic(template, title, items, null);
    }

    /** Each item on the word that says it (its cue, else its label's first word), in order; spread if not found. */
    static MotionGraphic timed(MotionGraphic graphic, double start, double end, List<Alignment.Word> words,
                               String tone) {
        List<Alignment.Word> inside = words.stream()
                .filter(w -> w.start() >= start - 0.5 && w.start() < end).toList();
        List<String> tokens = inside.stream().map(w -> norm(w.text())).toList();
        int n = graphic.items().size();
        double from = start + 0.4;
        double to = Math.max(from, end - READ_SECONDS);
        List<MotionGraphic.Item> out = new ArrayList<>();
        double previous = start;
        int searchFrom = 0;
        for (int i = 0; i < n; i++) {
            MotionGraphic.Item item = graphic.items().get(i);
            int hit = find(tokens, item.cue(), searchFrom);
            if (hit < 0) {
                hit = find(tokens, item.label(), searchFrom);
            }
            double at = hit >= 0 ? inside.get(hit).start() - LEAD
                    : (n == 1 ? from : from + (to - from) * i / (n - 1));
            if (hit >= 0) {
                searchFrom = hit + 1;
            }
            at = Math.max(at, previous + (i == 0 ? 0.2 : 0.35));
            at = Math.min(at, end - (i == n - 1 ? READ_SECONDS : TAIL));
            previous = at;
            out.add(new MotionGraphic.Item(item.label(), item.detail(), item.cue(), at));
        }
        // Keep the order when the last item was pulled earlier to stay readable: the ones before it move back too.
        for (int i = n - 2; i >= 0; i--) {
            double next = out.get(i + 1).at();
            if (out.get(i).at() > next - 0.35) {
                MotionGraphic.Item item = out.get(i);
                out.set(i, new MotionGraphic.Item(item.label(), item.detail(), item.cue(),
                        Math.max(start + 0.2, next - 0.35)));
            }
        }
        out.replaceAll(i -> new MotionGraphic.Item(i.label(), i.detail(), i.cue(), Math.round(i.at() * 100) / 100.0));
        String accent = "evidence".equals(graphic.template()) || isDark(tone) ? "red" : "sky";
        return new MotionGraphic(graphic.template(), graphic.title(), out, accent);
    }

    /** First position where the phrase's words follow each other (or its longest word alone). */
    static int find(List<String> tokens, String phrase, int from) {
        if (phrase == null || phrase.isBlank()) {
            return -1;
        }
        List<String> wanted = java.util.Arrays.stream(phrase.split("\\s+")).map(MotionDirector::norm)
                .filter(t -> !t.isEmpty()).toList();
        if (wanted.isEmpty()) {
            return -1;
        }
        for (int i = from; i + wanted.size() <= tokens.size(); i++) {
            boolean all = true;
            for (int k = 0; k < wanted.size() && all; k++) {
                all = tokens.get(i + k).equals(wanted.get(k));
            }
            if (all) {
                return i;
            }
        }
        String longest = wanted.stream().max(Comparator.comparingInt(String::length)).orElse("");
        if (longest.length() < 4) {
            return -1;
        }
        for (int i = from; i < tokens.size(); i++) {
            if (tokens.get(i).equals(longest)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isDark(String tone) {
        return tone != null && Set.of("tense", "dark", "shocked", "intense", "ominous", "serious")
                .contains(tone.toLowerCase(Locale.ROOT));
    }

    static String norm(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    static String cut(String text, int max) {
        String s = text.strip().replaceAll("\\s+", " ");
        if (s.length() <= max) {
            return s;
        }
        int space = s.lastIndexOf(' ', max);
        return (space > max / 2 ? s.substring(0, space) : s.substring(0, max)).strip();
    }

    private static int indexOf(List<Shot> list, String id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /** One line per candidate: id | time | a text card (its words) or a film shot | what is said during it. */
    private static String line(Shot shot, Alignment alignment) {
        String said = alignment.words().stream()
                .filter(w -> w.start() >= shot.start() - 0.5 && w.start() < shot.end() + 0.5)
                .map(Alignment.Word::text).collect(Collectors.joining(" "));
        String kind = ShotType.KINETIC_TEXT.equals(shot.type())
                ? "text card \"" + (shot.text() == null ? "" : shot.text()) + "\"" : "film";
        return "%s | %d:%02d | %s | %s".formatted(shot.id(), (int) shot.start() / 60, (int) shot.start() % 60, kind,
                said);
    }

    /** The story spine's twist and planted clues, so the board and the merge land on the payoff. */
    private String spine(StageContext context) {
        if (!context.files().exists(Stage.OUTLINE, "outline.json")) {
            return "(none)";
        }
        Outline outline = context.files().read(Stage.OUTLINE, "outline.json", Outline.class);
        if (outline.spine() == null) {
            return "(none)";
        }
        Map<String, Object> view = new HashMap<>();
        if (outline.spine().arc() != null) {
            view.put("twist", outline.spine().arc().twist());
        }
        if (outline.spine().plants() != null) {
            view.put("plants", outline.spine().plants());
        }
        return json.writeValueAsString(view);
    }
}
