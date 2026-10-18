package com.storyforge.orchestrator.provider.tts;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import com.storyforge.orchestrator.media.WavAudio;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "tts", havingValue = "kokoro")
public class KokoroTtsProvider implements TtsProvider {

    private final MediaSvcClient media;
    private final StoryForgeProperties.Narration narration;
    private final Path cacheDir;
    private final JsonMapper json;

    public KokoroTtsProvider(MediaSvcClient media, StoryForgeProperties properties, JsonMapper json) {
        this.media = media;
        this.narration = properties.narration();
        this.cacheDir = Path.of(properties.dataDir()).toAbsolutePath().resolve("cache").resolve("tts");
        this.json = json;
    }

    @Override
    public String name() {
        return "kokoro";
    }

    @Override
    public String voice() {
        return narration.voice();
    }

    @Override
    public double synthesize(Scene scene, Path outputWav) {
        return synthesize(scene, outputWav, 1.0, null);
    }

    @Override
    public double synthesize(Scene scene, Path outputWav, double speedFactor, String voice) {
        double sceneSpeed = scene.speed() > 0 ? scene.speed() : 1.0;
        String chosen = voice != null ? voice : narration.voice();
        double speed = Math.round(narration.speed() * sceneSpeed * speedFactor * 1000) / 1000.0;
        Path cached = cacheDir.resolve(hash(chosen + "|" + speed + "|" + narration.lang() + "|"
                + json.writeValueAsString(scene.segments())) + ".wav");
        try {
            if (!Files.isRegularFile(cached)) {
                Files.createDirectories(cacheDir);
                media.tts(scene.segments(), chosen, speed, narration.lang(), cached);
            }
            Files.copy(cached, outputWav, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return WavAudio.durationSec(outputWav);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
