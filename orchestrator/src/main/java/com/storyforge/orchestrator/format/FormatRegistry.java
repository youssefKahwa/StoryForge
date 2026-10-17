package com.storyforge.orchestrator.format;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FormatRegistry {

    private final Path formatsDir;
    private final JsonMapper json;
    private final Map<VideoFormat, FormatDefinition> cache = new ConcurrentHashMap<>();

    public FormatRegistry(StoryForgeProperties properties, JsonMapper json) {
        this.formatsDir = Path.of(properties.configDir()).resolve("formats");
        this.json = json;
    }

    public FormatDefinition get(VideoFormat format) {
        return cache.computeIfAbsent(format, this::load);
    }

    private FormatDefinition load(VideoFormat format) {
        Path file = formatsDir.resolve(format.id() + ".json");
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Format definition not found: " + file.toAbsolutePath());
        }
        return json.readValue(file, FormatDefinition.class);
    }
}
