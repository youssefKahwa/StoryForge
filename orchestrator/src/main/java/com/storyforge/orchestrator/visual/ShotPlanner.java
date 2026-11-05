package com.storyforge.orchestrator.visual;

import static com.storyforge.orchestrator.model.ShotType.CHARACTER;
import static com.storyforge.orchestrator.model.ShotType.DETAIL;
import static com.storyforge.orchestrator.model.ShotType.ESTABLISHING;
import static com.storyforge.orchestrator.model.ShotType.KINETIC_TEXT;
import static com.storyforge.orchestrator.model.ShotType.SYMBOLIC;

import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.model.ShotType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ShotPlanner {

    /** A shot may run up to this times the longest shot to end with its sentence instead of mid-sentence. */
    static final double SENTENCE_STRETCH = 1.6;

    public record Rules(double minSeconds, double maxSeconds, int maxSameType, double interruptEverySeconds) {
    }

    private record Piece(double start, double end, List<Word> words) {
    }

    private static final List<String> ROTATION = List.of(CHARACTER, DETAIL, SYMBOLIC);
    private static final List<String> TYPE_ORDER = List.of(ESTABLISHING, CHARACTER, DETAIL, SYMBOLIC, KINETIC_TEXT,
            ShotType.MAP, ShotType.TIMELINE, ShotType.CHART, ShotType.MOTION_HERO, ShotType.ARCHIVAL_PD);
    private static final List<String> MOTIONS = List.of("push_in", "pan_right", "pull_out", "pan_left");
    private static final Map<String, String> MODIFIERS = Map.of(
            ESTABLISHING, "wide establishing shot, sense of place and time",
            CHARACTER, "medium shot of an anonymous figure, expressive posture, face not identifiable",
            DETAIL, "macro close-up of a meaningful object, shallow depth of field",
            SYMBOLIC, "symbolic abstract composition, metaphorical imagery");
    private static final int KEY_PHRASE_WORDS = 7;

    private final Rules rules;

    public ShotPlanner(Rules rules) {
        this.rules = rules;
    }

    public ShotList plan(List<Scene> scenes, Alignment alignment, FormatDefinition.Style style, long baseSeed) {
        Map<String, SceneSpan> spans = alignment.scenes().stream()
                .collect(Collectors.toMap(SceneSpan::sceneId, Function.identity()));
        List<Shot> shots = new ArrayList<>();
        String lastType = null;
        int sameRun = 0;
        int rotation = 0;
        int motion = 0;
        double lastInterrupt = 0;
        String previousBeat = null;

        for (Scene scene : scenes) {
            SceneSpan span = spans.get(scene.id());
            if (span == null) {
                throw new IllegalStateException("Alignment has no span for scene " + scene.id());
            }
            List<Piece> pieces = cut(span, alignment.wordsOf(scene.id()));
            boolean onscreenPending = scene.onscreen() != null && !scene.onscreen().isBlank();
            boolean newBeat = !scene.beat().equals(previousBeat);
            int onscreenSlot = Math.min(1, pieces.size() - 1);
            Map<String, Integer> occurrences = new HashMap<>();
            rotation = scene.index();

            for (int p = 0; p < pieces.size(); p++) {
                Piece piece = pieces.get(p);
                String type;
                String text = null;
                if (piece.end() - lastInterrupt > rules.interruptEverySeconds()
                        || onscreenPending && p == onscreenSlot) {
                    type = KINETIC_TEXT;
                    text = onscreenPending ? scene.onscreen() : keyPhrase(piece.words(), scene);
                    onscreenPending = false;
                } else if (p == 0 && newBeat) {
                    type = ESTABLISHING;
                } else {
                    type = ROTATION.get(rotation++ % ROTATION.size());
                }
                if (type.equals(lastType) && sameRun >= rules.maxSameType() && !KINETIC_TEXT.equals(type)) {
                    type = ROTATION.get(rotation++ % ROTATION.size());
                    if (type.equals(lastType)) {
                        type = ROTATION.get(rotation++ % ROTATION.size());
                    }
                }
                sameRun = type.equals(lastType) ? sameRun + 1 : 1;
                lastType = type;
                if (ShotType.isInterrupt(type)) {
                    lastInterrupt = piece.start();
                }
                boolean graphic = ShotType.isGraphic(type);
                shots.add(new Shot(
                        "shot_%03d".formatted(shots.size() + 1),
                        scene.id(),
                        type,
                        round(piece.start()),
                        round(piece.end()),
                        graphic ? null : prompt(style, type, scene),
                        graphic ? "none" : MOTIONS.get(motion++ % MOTIONS.size()),
                        text,
                        baseSeed + scene.index() * 1000L + TYPE_ORDER.indexOf(type) * 50L
                                + occurrences.merge(type, 1, Integer::sum)));
            }
            previousBeat = scene.beat();
        }
        return new ShotList(alignment.durationSec(), shots);
    }

    private List<Piece> cut(SceneSpan span, List<Word> words) {
        List<Double> sentenceCuts = new ArrayList<>();
        List<Double> phraseCuts = new ArrayList<>();
        List<Double> wordCuts = new ArrayList<>();
        for (int i = 0; i < words.size() - 1; i++) {
            double cut = (words.get(i).end() + words.get(i + 1).start()) / 2;
            wordCuts.add(cut);
            if (words.get(i).endsSentence()) {
                sentenceCuts.add(cut);
            }
            if (words.get(i).endsPhrase()) {
                phraseCuts.add(cut);
            }
        }
        double min = rules.minSeconds();
        double max = rules.maxSeconds();
        double target = (min + max) / 2;
        List<Double> cuts = new ArrayList<>();
        double cursor = span.start();
        while (span.end() - cursor > max) {
            double lo = cursor + min;
            double hi = Math.min(cursor + max, span.end() - min);
            if (hi < lo) {
                hi = cursor + max;
            }
            // The picture changes where an idea ends: a sentence end, even if the shot runs a little long; a comma or
            // a word only when a sentence is very long. Cutting mid-sentence made pictures feel a beat off.
            Double cut = closest(sentenceCuts, lo, hi, cursor + target);
            if (cut == null) {
                cut = closest(sentenceCuts, lo, Math.min(cursor + max * SENTENCE_STRETCH, span.end() - min),
                        cursor + target);
            }
            if (cut == null) {
                cut = closest(phraseCuts, lo, hi, cursor + target);
            }
            if (cut == null) {
                cut = closest(wordCuts, lo, hi, cursor + target);
            }
            if (cut == null) {
                cut = Math.min(cursor + target, hi);
            }
            cuts.add(cut);
            cursor = cut;
        }
        List<Piece> pieces = new ArrayList<>();
        double start = span.start();
        for (double cut : cuts) {
            pieces.add(piece(start, cut, words));
            start = cut;
        }
        pieces.add(piece(start, span.end(), words));
        return pieces;
    }

    private static Piece piece(double start, double end, List<Word> words) {
        double mid = 1e-6;
        return new Piece(start, end, words.stream()
                .filter(w -> (w.start() + w.end()) / 2 >= start - mid && (w.start() + w.end()) / 2 < end + mid)
                .toList());
    }

    private static Double closest(List<Double> candidates, double lo, double hi, double target) {
        Double best = null;
        for (double c : candidates) {
            if (c >= lo && c <= hi && (best == null || Math.abs(c - target) < Math.abs(best - target))) {
                best = c;
            }
        }
        return best;
    }

    private static String keyPhrase(List<Word> words, Scene scene) {
        List<String> source = words.isEmpty() ? scene.words() : words.stream().map(Word::text).toList();
        return source.stream().limit(KEY_PHRASE_WORDS).collect(Collectors.joining(" "))
                .replaceAll("[,;:—–-]+$", "");
    }

    private static String prompt(FormatDefinition.Style style, String type, Scene scene) {
        String subject = scene.visual() != null ? scene.visual() : scene.narration();
        return String.join(", ", style.promptPrefix(), MODIFIERS.getOrDefault(type, type), subject);
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }
}
