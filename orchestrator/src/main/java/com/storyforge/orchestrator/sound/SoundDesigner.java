package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.sound.MusicLibrary.Track;
import com.storyforge.orchestrator.sound.SoundPlan.MusicSegment;
import com.storyforge.orchestrator.sound.SoundPlan.SfxCue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class SoundDesigner {

    /** Scene music mood meaning "no music here" (owner revision). */
    public static final String SILENCE = "none";

    public static final double CROSSFADE = 1.5;
    private static final double MIN_SEGMENT = 20;
    private static final double MIN_SFX_GAP = 6;
    private static final String DEFAULT_MOOD = "mystery";
    private static final Map<String, String> PRESET_WORDS = presetWords();
    private static final Map<String, Double> PRESET_GAIN = Map.of(
            "whoosh", -14.0, "low_hit", -10.0, "riser", -16.0, "wind", -24.0, "tick", -18.0, "paper", -16.0);
    private static final Map<String, Double> PRESET_LENGTH = Map.of("riser", 3.0);

    private SoundDesigner() {
    }

    public static SoundPlan plan(List<Scene> scenes, Alignment alignment, List<EdlChapter> chapters,
                                 Map<String, List<Track>> library, long seed,
                                 Function<String, String> sfxFile, double musicOffsetDb) {
        Map<String, SceneSpan> spans = alignment.scenes().stream()
                .collect(Collectors.toMap(SceneSpan::sceneId, Function.identity()));
        return new SoundPlan(music(scenes, spans, alignment.durationSec(), library, seed, musicOffsetDb),
                sfx(scenes, spans, alignment, chapters, sfxFile));
    }

    static List<MusicSegment> music(List<Scene> scenes, Map<String, SceneSpan> spans, double duration,
                                    Map<String, List<Track>> library, long seed, double musicOffsetDb) {
        List<double[]> ranges = new ArrayList<>();
        List<String> segmentMoods = new ArrayList<>();
        List<Double> intensities = new ArrayList<>();
        String current = DEFAULT_MOOD;
        double intensity = 0.5;
        for (Scene scene : scenes) {
            SceneSpan span = spans.get(scene.id());
            if (scene.music() != null) {
                current = SILENCE.equals(scene.music().mood()) ? SILENCE
                        : MusicLibrary.canonicalMood(scene.music().mood());
                intensity = scene.music().intensity();
            }
            int last = segmentMoods.size() - 1;
            if (last >= 0 && segmentMoods.get(last).equals(current)) {
                ranges.get(last)[1] = span.end();
            } else {
                ranges.add(new double[]{span.start(), span.end()});
                segmentMoods.add(current);
                intensities.add(intensity);
            }
        }
        for (int i = ranges.size() - 1; i > 0; i--) {
            if (ranges.get(i)[1] - ranges.get(i)[0] < MIN_SEGMENT) {
                ranges.get(i - 1)[1] = ranges.get(i)[1];
                ranges.remove(i);
                segmentMoods.remove(i);
                intensities.remove(i);
            }
        }
        List<MusicSegment> segments = new ArrayList<>();
        String previousFile = null;
        for (int i = 0; i < ranges.size(); i++) {
            if (SILENCE.equals(segmentMoods.get(i))) {
                continue;
            }
            Track track = pick(segmentMoods.get(i), library, seed + i, previousFile);
            if (track == null) {
                return List.of();
            }
            double start = Math.max(0, ranges.get(i)[0] - (i == 0 ? 0 : CROSSFADE / 2));
            double end = Math.min(duration, ranges.get(i)[1] + (i == ranges.size() - 1 ? 0 : CROSSFADE / 2));
            double gain = -20 + 6 * Math.max(0, Math.min(1, intensities.get(i))) + musicOffsetDb;
            segments.add(new MusicSegment(track.mood(), track.file().toString(), track.relativeName(),
                    round(start), round(end), round(gain)));
            previousFile = track.file().toString();
        }
        return segments;
    }

    private static Track pick(String mood, Map<String, List<Track>> library, long seed, String avoid) {
        List<String> order = new ArrayList<>();
        order.add(mood);
        order.addAll(MusicLibrary.fallbacks(mood));
        library.keySet().stream().filter(m -> !order.contains(m)).forEach(order::add);
        for (String candidate : order) {
            List<Track> tracks = library.getOrDefault(candidate, List.of());
            if (tracks.isEmpty()) {
                continue;
            }
            Track track = tracks.get((int) Math.floorMod(seed, tracks.size()));
            if (track.file().toString().equals(avoid) && tracks.size() > 1) {
                track = tracks.get((int) Math.floorMod(seed + 1, tracks.size()));
            }
            return track;
        }
        return null;
    }

    static List<SfxCue> sfx(List<Scene> scenes, Map<String, SceneSpan> spans, Alignment alignment,
                            List<EdlChapter> chapters, Function<String, String> sfxFile) {
        List<SfxCue> cues = new ArrayList<>();
        for (int i = 1; i < chapters.size(); i++) {
            double at = Math.max(0, chapters.get(i).start() - 0.45);
            cues.add(new SfxCue("whoosh", sfxFile.apply("whoosh"), round(at), PRESET_GAIN.get("whoosh"),
                    "chapter " + (i + 1)));
        }
        for (Scene scene : scenes) {
            SceneSpan span = spans.get(scene.id());
            for (Scene.SfxCue cue : scene.sfx()) {
                String preset = preset(cue.name());
                if (preset == null) {
                    continue;
                }
                double at = anchorTime(alignment.wordsOf(scene.id()), cue.anchor(), span.start());
                at -= PRESET_LENGTH.getOrDefault(preset, 0.0);
                cues.add(new SfxCue(preset, sfxFile.apply(preset), round(Math.max(0, at)),
                        PRESET_GAIN.get(preset), "scene " + scene.id() + ": " + cue.name()));
            }
        }
        cues.sort((a, b) -> Double.compare(a.start(), b.start()));
        List<SfxCue> spaced = new ArrayList<>();
        for (SfxCue cue : cues) {
            if (spaced.isEmpty() || cue.start() - spaced.get(spaced.size() - 1).start() >= MIN_SFX_GAP) {
                spaced.add(cue);
            }
        }
        return spaced;
    }

    static String preset(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return PRESET_WORDS.entrySet().stream().filter(entry -> key.contains(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static double anchorTime(List<Word> words, String anchor, double fallback) {
        if (anchor == null || anchor.isBlank()) {
            return fallback;
        }
        String target = normalize(anchor);
        return words.stream().filter(word -> normalize(word.text()).startsWith(target)).findFirst()
                .map(Word::start).orElse(fallback);
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Map<String, String> presetWords() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String word : List.of("whoosh", "swoosh", "swish", "transition", "swipe")) {
            map.put(word, "whoosh");
        }
        for (String word : List.of("hit", "boom", "impact", "thud", "slam", "reveal", "drum", "bass")) {
            map.put(word, "low_hit");
        }
        for (String word : List.of("rise", "riser", "build", "swell", "tension")) {
            map.put(word, "riser");
        }
        for (String word : List.of("wind", "storm", "sea", "wave", "ocean", "ambien", "creak", "rain", "desert")) {
            map.put(word, "wind");
        }
        for (String word : List.of("tick", "clock", "click", "telegraph", "morse")) {
            map.put(word, "tick");
        }
        for (String word : List.of("paper", "page", "letter", "rustle", "book", "document")) {
            map.put(word, "paper");
        }
        return map;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
