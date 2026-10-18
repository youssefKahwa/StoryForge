package com.storyforge.orchestrator.voice;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class VoiceCatalog {

    public record Voice(String id, String label, String gender, String accent, String grade) {
    }

    private static final List<Voice> VOICES = List.of(
            new Voice("af_heart", "Heart", "female", "American", "A"),
            new Voice("af_bella", "Bella", "female", "American", "A-"),
            new Voice("bf_emma", "Emma", "female", "British", "B-"),
            new Voice("am_michael", "Michael", "male", "American", "C+"),
            new Voice("am_fenrir", "Fenrir", "male", "American", "C+"),
            new Voice("am_puck", "Puck", "male", "American", "C+"),
            new Voice("bm_george", "George", "male", "British", "C"),
            new Voice("bm_fable", "Fable", "male", "British", "C"));
    private static final String SAMPLE_TEXT = "By sunrise, the caravan had lost two men. And the desert was only "
            + "getting started. This is the story nobody in the village wanted told.";

    private final MediaSvcClient media;
    private final StoryForgeProperties properties;
    private final Path samplesDir;

    public VoiceCatalog(MediaSvcClient media, StoryForgeProperties properties) {
        this.media = media;
        this.properties = properties;
        this.samplesDir = Path.of(properties.dataDir()).toAbsolutePath().resolve("samples");
    }

    public List<Voice> voices() {
        return VOICES;
    }

    public String defaultVoice() {
        return properties.narration().voice();
    }

    public Optional<Path> sample(String id) {
        if (VOICES.stream().noneMatch(voice -> voice.id().equals(id))) {
            return Optional.empty();
        }
        Path file = samplesDir.resolve("voice_" + id + ".wav");
        if (!Files.isRegularFile(file)) {
            media.tts(List.of(Scene.Segment.text(SAMPLE_TEXT, false)), id, properties.narration().speed(),
                    properties.narration().lang(), file);
        }
        return Optional.of(file);
    }
}
