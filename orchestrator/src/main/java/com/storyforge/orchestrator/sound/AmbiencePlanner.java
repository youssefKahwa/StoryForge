package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Scene;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A quiet ambience bed under every scene (room tone, city, rain, crowd...), chosen from what the scene shows and
 * says. Real places have sound; a voice over silence feels like a slideshow. Neighbouring scenes with the same bed
 * are merged, and very short beds join the previous one so the background does not flicker.
 */
public final class AmbiencePlanner {

    public record Bed(String category, double start, double end) {
        public double length() {
            return end - start;
        }
    }

    public static final String DEFAULT = "amb_room";
    private static final double MIN_BED = 8;
    private static final Map<String, List<String>> WORDS = words();

    private AmbiencePlanner() {
    }

    public static List<Bed> plan(List<Scene> scenes, Map<String, SceneSpan> spans) {
        List<Bed> beds = new ArrayList<>();
        for (Scene scene : scenes) {
            SceneSpan span = spans.get(scene.id());
            if (span == null) {
                continue;
            }
            String category = category(scene);
            Bed last = beds.isEmpty() ? null : beds.get(beds.size() - 1);
            if (last != null && (last.category().equals(category) || span.end() - span.start() < MIN_BED)) {
                beds.set(beds.size() - 1, new Bed(last.category(), last.start(), span.end()));
            } else {
                beds.add(new Bed(category, span.start(), span.end()));
            }
        }
        return beds;
    }

    static String category(Scene scene) {
        String text = (nz(scene.visual()) + " " + nz(scene.narration()) + " "
                + String.join(" ", scene.sfx().stream().map(Scene.SfxCue::name).toList())).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : WORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(word -> text.contains(word))) {
                return entry.getKey();
            }
        }
        return DEFAULT;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, List<String>> words() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("amb_rain", List.of("rain", "storm", "downpour", "drizzle", "thunder"));
        map.put("amb_fire", List.of("fire", "flame", "burning", "blaze", "explosion", "explod"));
        map.put("amb_crowd", List.of("crowd", "audience", "cheering", "stadium", "protest", "mob", "basement",
                "brawl", "riot"));
        map.put("amb_bar", List.of("in a bar", "at the bar", "bar counter", "bartender", "barman", "pub ", "tavern",
                "nightclub"));
        map.put("amb_office", List.of("office", "cubicle", "desk", "corporate", "meeting", "boss", "photocop"));
        map.put("amb_industrial", List.of("factory", "warehouse", "machine", "industrial", "workshop"));
        map.put("amb_city", List.of("city", "street", "traffic", "downtown", "highway", "airport", "car "));
        map.put("amb_wind", List.of("wind", "desert", "ocean", "sea ", "mountain", "cliff", "snow", "island"));
        map.put("amb_night", List.of("night", "insomnia", "sleepless", "moon", "midnight"));
        return map;
    }
}
