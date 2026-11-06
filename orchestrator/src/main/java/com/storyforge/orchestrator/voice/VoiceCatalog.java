package com.storyforge.orchestrator.voice;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import com.storyforge.orchestrator.settings.PreferencesService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class VoiceCatalog {

    public record Voice(String id, String label, String gender, String accent, String grade) {
    }

    /**
     * NVIDIA Magpie voices (free API, expressive, with emotion variants) through the human delivery engine, then the
     * local Kokoro voices. "magpie:Jason" is the fast-recap narrator the owner chose.
     */
    private static final List<Voice> VOICES = List.of(
            new Voice("magpie:Jason", "Jason (expressive)", "male", "American, NVIDIA Magpie", "A"),
            new Voice("magpie:Leo", "Leo (expressive)", "male", "American, NVIDIA Magpie", "A-"),
            new Voice("magpie:Ray", "Ray (expressive)", "male", "American, NVIDIA Magpie", "A-"),
            new Voice("magpie:Aria", "Aria (expressive)", "female", "American, NVIDIA Magpie", "A-"),
            new Voice("magpie:Sofia", "Sofia (expressive)", "female", "American, NVIDIA Magpie", "A-"),
            new Voice("magpie:Mia", "Mia (expressive)", "female", "American, NVIDIA Magpie", "B+"),
            new Voice("am_michael:60+am_fenrir:40", "Channel blend (Michael + Fenrir)", "male", "American, local",
                    "B"),
            new Voice("af_heart", "Heart", "female", "American, local", "A"),
            new Voice("af_bella", "Bella", "female", "American, local", "A-"),
            new Voice("bf_emma", "Emma", "female", "British, local", "B-"),
            new Voice("am_michael", "Michael", "male", "American, local", "C+"),
            new Voice("am_fenrir", "Fenrir", "male", "American, local", "C+"),
            new Voice("am_puck", "Puck", "male", "American, local", "C+"),
            new Voice("bm_george", "George", "male", "British, local", "C"),
            new Voice("bm_fable", "Fable", "male", "British, local", "C"));
    private static final String SAMPLE_TEXT = "By sunrise, the caravan had lost two men. And the desert was only "
            + "getting started. This is the story nobody in the village wanted told.";
    /** The Magpie sample is written for the ear, with a mood switch and a held beat, like a real script. */
    private static final List<Map<String, Object>> TALKING_SAMPLE = List.of(
            Map.of("kind", "text", "text", "So picture this. A guy with no name, no sleep, and a job so grey you can "
                    + "basically hear the office lights buzzing."),
            Map.of("kind", "beat", "ms", 0),
            Map.of("kind", "mood", "text", "neutral"),
            Map.of("kind", "text", "text", "And then a stranger sits down right next to him, and nothing is ever the "
                    + "same again."));

    private final MediaSvcClient media;
    private final StoryForgeProperties properties;
    private final PreferencesService preferences;
    private final Path samplesDir;

    public VoiceCatalog(MediaSvcClient media, StoryForgeProperties properties, PreferencesService preferences) {
        this.media = media;
        this.properties = properties;
        this.preferences = preferences;
        this.samplesDir = Path.of(properties.dataDir()).toAbsolutePath().resolve("samples");
    }

    public List<Voice> voices() {
        return VOICES;
    }

    public String defaultVoice() {
        return preferences.get().defaultVoice();
    }

    public Optional<Path> sample(String id) {
        if (VOICES.stream().noneMatch(voice -> voice.id().equals(id))) {
            return Optional.empty();
        }
        Path file = samplesDir.resolve("voice_" + id.replaceAll("[^A-Za-z0-9_+-]", "_") + ".wav");
        if (!Files.isRegularFile(file)) {
            if (id.startsWith("magpie:")) {
                media.speak(TALKING_SAMPLE, "magpie", id.substring("magpie:".length()), 1.0,
                        properties.narration().lang(), "hype", properties.narration().deliveryPace(),
                        properties.narration().withBreaths(), Map.of(),
                        "sample", file);
            } else {
                media.tts(List.of(Scene.Segment.text(SAMPLE_TEXT, false)), id, properties.narration().speed(),
                        properties.narration().lang(), file);
            }
        }
        return Optional.of(file);
    }
}
