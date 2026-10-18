package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class SfxLibrary {

    public static final LicenseRecord LICENSE = new LicenseRecord("generated", "storyforge-sfx-synth", null,
            "Synthesized by StoryForge media-svc from noise and tones", null);

    private final Path root;
    private final MediaSvcClient media;

    public SfxLibrary(StoryForgeProperties properties, MediaSvcClient media) {
        this.root = Path.of(properties.sound().libraryDir()).toAbsolutePath().resolve("sfx");
        this.media = media;
    }

    public String file(String preset) {
        Path file = root.resolve(preset + ".wav");
        if (!Files.isRegularFile(file)) {
            media.sfx(preset, file);
        }
        return file.toString();
    }
}
