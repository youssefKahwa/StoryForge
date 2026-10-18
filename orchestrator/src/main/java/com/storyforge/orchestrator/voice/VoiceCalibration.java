package com.storyforge.orchestrator.voice;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class VoiceCalibration {

    private static final double LEARNING_RATE = 0.5;
    private static final double MIN_WPM = 90;
    private static final double MAX_WPM = 220;

    public record Measurement(double wordsPerMinute, int samples) {
    }

    private final Path file;
    private final JsonMapper json;
    private final int fallback;

    public VoiceCalibration(StoryForgeProperties properties, JsonMapper json) {
        this.file = Path.of(properties.dataDir()).toAbsolutePath().resolve("calibration").resolve("voices.json");
        this.json = json;
        this.fallback = properties.video().wordsPerMinute();
    }

    public synchronized int wordsPerMinute(String voice) {
        Measurement measurement = load().get(voice);
        return measurement == null ? fallback : (int) Math.round(measurement.wordsPerMinute());
    }

    public synchronized void record(String voice, int words, double speechSeconds) {
        if (voice == null || words < 50 || speechSeconds < 20) {
            return;
        }
        double measured = Math.max(MIN_WPM, Math.min(MAX_WPM, words * 60.0 / speechSeconds));
        Map<String, Measurement> all = load();
        Measurement previous = all.get(voice);
        Measurement next = previous == null ? new Measurement(measured, 1)
                : new Measurement(previous.wordsPerMinute() + LEARNING_RATE * (measured - previous.wordsPerMinute()),
                previous.samples() + 1);
        all.put(voice, next);
        try {
            Files.createDirectories(file.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(file, all);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Measurement> load() {
        if (!Files.isRegularFile(file)) {
            return new HashMap<>();
        }
        return new HashMap<>(json.readValue(file, new TypeReference<Map<String, Measurement>>() { }));
    }
}
