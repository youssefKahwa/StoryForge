package com.storyforge.orchestrator.host;

import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl.HostAppearance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides when the channel's talking logo pops in (the opening, rhetorical questions, the outro) and which mouth
 * frame to show on every video frame, from the narration's loudness: a cartoon lip-sync without any face or voice.
 */
public final class HostPlanner {

    private static final double MIN_SECONDS = 2.5;
    private static final double MAX_SECONDS = 9;
    private static final double MIN_GAP_SECONDS = 45;
    private static final double MAX_SHARE = 0.2;
    private static final double OUTRO_SECONDS = 12;
    private static final double SILENCE_DB = -42;
    private static final double FULL_OPEN_DB = -14;

    private HostPlanner() {
    }

    public record Sentence(double start, double end, boolean question) {
    }

    /** Time ranges where the host is on screen. */
    public static List<double[]> appearances(List<Word> words, double duration) {
        List<Sentence> sentences = sentences(words);
        List<double[]> picked = new ArrayList<>();
        if (sentences.isEmpty() || duration < 20) {
            return picked;
        }
        Sentence opening = sentences.stream().filter(s -> s.question() && s.start() < 40).findFirst()
                .orElse(sentences.get(0));
        picked.add(span(opening, duration));
        for (Sentence sentence : sentences) {
            double[] last = picked.get(picked.size() - 1);
            if (sentence.question() && sentence.start() - last[1] >= MIN_GAP_SECONDS
                    && sentence.start() < duration - OUTRO_SECONDS - MIN_GAP_SECONDS / 3) {
                picked.add(span(sentence, duration));
            }
        }
        double outroStart = Math.max(duration - OUTRO_SECONDS, picked.get(picked.size() - 1)[1] + 5);
        if (duration - outroStart >= MIN_SECONDS) {
            picked.add(new double[]{round(outroStart), round(duration - 0.4)});
        }
        double budget = duration * MAX_SHARE;
        List<double[]> kept = new ArrayList<>();
        double used = 0;
        for (double[] range : picked) {
            double length = range[1] - range[0];
            if (used + length <= budget || kept.isEmpty()) {
                kept.add(range);
                used += length;
            }
        }
        kept.sort(Comparator.comparingDouble(r -> r[0]));
        return kept;
    }

    /**
     * Mouth frame per video frame: silence shows the most closed frame, speech picks a frame whose openness matches
     * the loudness, rotating between close candidates so the mouth never freezes on one shape.
     */
    public static List<HostAppearance> mouths(List<double[]> ranges, double[] dbPerFrame, double[] openness, int fps) {
        List<Integer> byOpenness = new ArrayList<>();
        for (int i = 0; i < openness.length; i++) {
            byOpenness.add(i);
        }
        byOpenness.sort(Comparator.comparingDouble(i -> openness[i]));
        int closed = byOpenness.get(0);
        List<HostAppearance> result = new ArrayList<>();
        double level = 0;
        for (double[] range : ranges) {
            int from = (int) Math.round(range[0] * fps);
            int to = (int) Math.round(range[1] * fps);
            List<Integer> frames = new ArrayList<>();
            for (int f = from; f < to; f++) {
                double db = f < dbPerFrame.length ? dbPerFrame[f] : -90;
                double target = db <= SILENCE_DB ? 0 : Math.min(1, (db - SILENCE_DB) / (FULL_OPEN_DB - SILENCE_DB));
                level = target > level ? target : level * 0.6 + target * 0.4;
                if (level < 0.08) {
                    frames.add(closed);
                    continue;
                }
                int rank = (int) Math.round(level * (byOpenness.size() - 1));
                int jitter = (f % 3) - 1;
                int index = Math.max(1, Math.min(byOpenness.size() - 1, rank + jitter * 2));
                frames.add(byOpenness.get(index));
            }
            result.add(new HostAppearance(round(range[0]), round(range[1]), frames));
        }
        return result;
    }

    static List<Sentence> sentences(List<Word> words) {
        List<Sentence> sentences = new ArrayList<>();
        Double start = null;
        for (Word word : words) {
            if (start == null) {
                start = word.start();
            }
            if (word.endsSentence()) {
                sentences.add(new Sentence(start, word.end(), word.text().replaceAll("[\"'”’)\\]]+$", "").endsWith("?")));
                start = null;
            }
        }
        return sentences;
    }

    private static double[] span(Sentence sentence, double duration) {
        double start = Math.max(0.3, sentence.start() - 0.25);
        double end = Math.min(duration - 0.3, Math.max(sentence.end() + 0.5, start + MIN_SECONDS));
        return new double[]{round(start), round(Math.min(end, start + MAX_SECONDS))};
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }
}
