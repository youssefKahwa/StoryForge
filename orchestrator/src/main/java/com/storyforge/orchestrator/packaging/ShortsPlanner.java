package com.storyforge.orchestrator.packaging;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlShot;
import com.storyforge.orchestrator.model.Scene;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ShortsPlanner {

    public static final double END_CARD_SECONDS = 2.5;
    private static final double MIN_SECONDS = 15;
    private static final List<String> PRIORITY = List.of("cold_open", "midpoint_twist", "climax", "dark_moment",
            "escalation_3", "escalation_2", "escalation_1", "hook_thesis", "verdict", "counter_reading");

    public record Moment(String sceneId, String beat, double start, double end) {

        public double length() {
            return end - start;
        }
    }

    private ShortsPlanner() {
    }

    public static List<Moment> pick(List<Scene> scenes, Alignment alignment, int count, double maxSeconds) {
        Map<String, SceneSpan> spans = alignment.scenes().stream()
                .collect(Collectors.toMap(SceneSpan::sceneId, Function.identity()));
        double maxContent = maxSeconds - END_CARD_SECONDS;
        List<Moment> moments = new ArrayList<>();
        List<String> beats = new ArrayList<>(PRIORITY);
        scenes.stream().map(Scene::beat).distinct().filter(beat -> !beats.contains(beat)).forEach(beats::add);
        for (String beat : beats) {
            if (moments.size() >= count) {
                break;
            }
            for (int i = 0; i < scenes.size(); i++) {
                if (!scenes.get(i).beat().equals(beat)) {
                    continue;
                }
                double start = spans.get(scenes.get(i).id()).start();
                double end = start;
                for (int j = i; j < scenes.size(); j++) {
                    double sceneEnd = spans.get(scenes.get(j).id()).end();
                    if (sceneEnd - start > maxContent) {
                        break;
                    }
                    end = sceneEnd;
                }
                if (end == start) {
                    end = lastSentenceEnd(alignment.words(), start, start + maxContent);
                }
                Moment moment = new Moment(scenes.get(i).id(), beat, round(start), round(end));
                if (moment.length() >= MIN_SECONDS && moments.stream().noneMatch(m -> overlaps(m, moment))) {
                    moments.add(moment);
                }
                break;
            }
        }
        return moments;
    }

    public static Map<String, Object> spec(Moment moment, Edl edl, Alignment alignment, String title, String audioFile) {
        double length = moment.length();
        List<Map<String, Object>> shots = new ArrayList<>();
        for (EdlShot shot : edl.shots()) {
            if (shot.end() <= moment.start() || shot.start() >= moment.end() || "kinetic_text".equals(shot.type())) {
                continue;
            }
            Map<String, Object> clipped = new LinkedHashMap<>();
            clipped.put("id", shot.id());
            clipped.put("asset", shot.asset());
            clipped.put("motion", shot.motion());
            clipped.put("media", shot.media());
            clipped.put("credit", shot.credit());
            clipped.put("map", shot.map());
            clipped.put("start", round(Math.max(0, shot.start() - moment.start())));
            clipped.put("end", round(Math.min(length, shot.end() - moment.start())));
            shots.add(clipped);
        }
        for (int i = 0; i + 1 < shots.size(); i++) {
            shots.get(i).put("end", shots.get(i + 1).get("start"));
        }
        if (!shots.isEmpty()) {
            shots.get(0).put("start", 0.0);
            shots.get(shots.size() - 1).put("end", round(length + END_CARD_SECONDS));
        }
        List<Map<String, Object>> words = alignment.words().stream()
                .filter(word -> word.start() >= moment.start() && word.end() <= moment.end())
                .map(word -> Map.<String, Object>of("text", word.text(), "start", round(word.start() - moment.start()),
                        "end", round(word.end() - moment.start())))
                .toList();
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("width", 1080);
        spec.put("height", 1920);
        spec.put("fps", edl.fps());
        spec.put("durationSec", round(length + END_CARD_SECONDS));
        spec.put("contentSec", round(length));
        spec.put("title", title);
        spec.put("words", words);
        spec.put("shots", shots);
        spec.put("audioFile", audioFile);
        spec.put("audioStartSec", moment.start());
        spec.put("style", edl.style());
        return spec;
    }

    private static double lastSentenceEnd(List<Word> words, double from, double limit) {
        double best = limit;
        for (Word word : words) {
            if (word.start() >= from && word.end() <= limit && word.endsSentence()) {
                best = word.end() + 0.3;
            }
        }
        return Math.min(best, limit);
    }

    private static boolean overlaps(Moment a, Moment b) {
        return a.start() < b.end() && b.start() < a.end();
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
