package com.storyforge.orchestrator.script;

import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.Scene.MusicCue;
import com.storyforge.orchestrator.model.Scene.Segment;
import com.storyforge.orchestrator.model.Scene.SfxCue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class SfmParser {

    private static final Pattern SCENE_HEADER =
            Pattern.compile("^##\\s*SCENE\\s+([A-Za-z0-9_-]+)\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("\\[(/?)([A-Za-z_]+)(?::\\s*([^\\]]*))?]");
    private static final Pattern DIRECTIVE = Pattern.compile("^@([a-zA-Z_]+):\\s*(.*)$");
    private static final Pattern SFX = Pattern.compile("^([^@]+?)(?:\\s*@\\s*\"?([^\"]*)\"?)?$");

    private SfmParser() {
    }

    public static List<Scene> parse(String markup) {
        List<Scene> scenes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        SceneBuilder current = null;
        int lineNumber = 0;
        for (String rawLine : markup.split("\\R")) {
            lineNumber++;
            String line = rawLine.strip();
            Matcher header = SCENE_HEADER.matcher(line);
            if (header.matches()) {
                if (current != null) {
                    scenes.add(current.build());
                }
                String id = header.group(1);
                if (!ids.add(id)) {
                    throw new SfmParseException("Duplicate scene id " + id + " at line " + lineNumber);
                }
                current = new SceneBuilder(id, scenes.size());
                current.header(header.group(2));
                continue;
            }
            if (current == null || line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher directive = DIRECTIVE.matcher(line);
            if (directive.matches()) {
                current.directive(directive.group(1).toLowerCase(Locale.ROOT), directive.group(2).strip());
            } else {
                current.text(line);
            }
        }
        if (current != null) {
            scenes.add(current.build());
        }
        if (scenes.isEmpty()) {
            throw new SfmParseException("No scenes found (expected '## SCENE <id> | beat: ...' headers)");
        }
        return scenes;
    }

    static int parseMillis(String value) {
        String v = value.strip().toLowerCase(Locale.ROOT);
        if (v.endsWith("ms")) {
            return (int) Math.round(Double.parseDouble(v.substring(0, v.length() - 2).strip()));
        }
        if (v.endsWith("s")) {
            return (int) Math.round(Double.parseDouble(v.substring(0, v.length() - 1).strip()) * 1000);
        }
        return (int) Math.round(Double.parseDouble(v));
    }

    static double parseSeconds(String value) {
        String v = value.strip().toLowerCase(Locale.ROOT);
        if (v.endsWith("ms")) {
            return Double.parseDouble(v.substring(0, v.length() - 2).strip()) / 1000.0;
        }
        if (v.endsWith("s")) {
            return Double.parseDouble(v.substring(0, v.length() - 1).strip());
        }
        return Double.parseDouble(v);
    }

    private static String unquote(String value) {
        String v = value.strip();
        if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("“") && v.endsWith("”"))) {
            return v.substring(1, v.length() - 1).strip();
        }
        return v;
    }

    private static final class SceneBuilder {

        private final String id;
        private final int index;
        private String beat = "unknown";
        private double durationTarget;
        private String voice = "narrator_main";
        private String tone;
        private String pace;
        private String emotion;
        private double speed = 1.0;
        private int silenceBefore;
        private int silenceAfter;
        private boolean emphasis;
        private final List<Segment> segments = new ArrayList<>();
        private final List<String> visuals = new ArrayList<>();
        private final List<SfxCue> sfx = new ArrayList<>();
        private MusicCue music;
        private String onscreen;
        private final List<String> claims = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        SceneBuilder(String id, int index) {
            this.id = id;
            this.index = index;
        }

        void header(String attributes) {
            for (String part : attributes.split("\\|")) {
                int colon = part.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = part.substring(0, colon).strip().toLowerCase(Locale.ROOT);
                String value = part.substring(colon + 1).strip();
                switch (key) {
                    case "beat" -> beat = value;
                    case "duration_target", "duration" -> durationTarget = safeSeconds(value);
                    default -> warnings.add("Unknown header attribute '" + key + "'");
                }
            }
        }

        void text(String line) {
            Matcher tag = TAG.matcher(line);
            int cursor = 0;
            while (tag.find()) {
                addText(line.substring(cursor, tag.start()));
                applyTag(tag.group(1).equals("/"), tag.group(2).toUpperCase(Locale.ROOT), tag.group(3));
                cursor = tag.end();
            }
            addText(line.substring(cursor));
        }

        private void addText(String chunk) {
            String text = chunk.replaceAll("\\s+", " ").strip();
            if (!text.isEmpty()) {
                segments.add(Segment.text(text, emphasis));
            }
        }

        private void applyTag(boolean closing, String name, String value) {
            try {
                switch (name) {
                    case "EMPHASIS" -> emphasis = !closing;
                    case "PAUSE" -> segments.add(Segment.pause(parseMillis(value)));
                    case "VOICE" -> voice = value.strip();
                    case "TONE" -> tone = value.strip();
                    case "PACE" -> pace = value.strip();
                    case "EMOTION" -> emotion = value.strip();
                    case "SPEED" -> speed = Double.parseDouble(value.strip());
                    case "SILENCE_BEFORE" -> silenceBefore = parseMillis(value);
                    case "SILENCE_AFTER" -> silenceAfter = parseMillis(value);
                    default -> warnings.add("Unknown tag [" + name + "]");
                }
            } catch (RuntimeException e) {
                warnings.add("Invalid value for [" + name + "]: " + value);
            }
        }

        void directive(String name, String value) {
            switch (name) {
                case "visual" -> visuals.add(value);
                case "sfx" -> {
                    Matcher m = SFX.matcher(value);
                    if (m.matches()) {
                        sfx.add(new SfxCue(m.group(1).strip(), m.group(2) == null ? null : m.group(2).strip()));
                    }
                }
                case "music" -> music = parseMusic(value);
                case "onscreen" -> onscreen = unquote(value);
                case "claims" -> claims.addAll(List.of(value.split("\\s*,\\s*")).stream()
                        .filter(c -> !c.isBlank()).toList());
                default -> warnings.add("Unknown directive @" + name);
            }
        }

        private MusicCue parseMusic(String value) {
            String mood = null;
            double intensity = 0.5;
            for (String pair : value.split(",")) {
                String[] kv = pair.split("=", 2);
                if (kv.length != 2) {
                    continue;
                }
                switch (kv[0].strip().toLowerCase(Locale.ROOT)) {
                    case "mood" -> mood = kv[1].strip();
                    case "intensity" -> intensity = safeDouble(kv[1], 0.5);
                    default -> warnings.add("Unknown music attribute " + kv[0].strip());
                }
            }
            return new MusicCue(mood, intensity);
        }

        private double safeSeconds(String value) {
            try {
                return parseSeconds(value);
            } catch (RuntimeException e) {
                warnings.add("Invalid duration_target: " + value);
                return 0;
            }
        }

        private static double safeDouble(String value, double fallback) {
            try {
                return Double.parseDouble(value.strip());
            } catch (RuntimeException e) {
                return fallback;
            }
        }

        Scene build() {
            String narration = segments.stream().filter(s -> !s.silent()).map(Segment::text)
                    .collect(Collectors.joining(" ")).replaceAll("\\s+", " ").strip();
            if (narration.isEmpty()) {
                warnings.add("Scene has no narration");
            }
            return new Scene(id, index, beat, durationTarget, voice, tone, pace, emotion, speed, silenceBefore,
                    silenceAfter, List.copyOf(segments), narration,
                    visuals.isEmpty() ? null : String.join("; ", visuals), List.copyOf(sfx), music, onscreen,
                    List.copyOf(claims), List.copyOf(warnings));
        }
    }
}
