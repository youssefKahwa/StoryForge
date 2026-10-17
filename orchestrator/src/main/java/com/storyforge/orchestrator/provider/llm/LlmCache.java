package com.storyforge.orchestrator.provider.llm;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class LlmCache {

    private final Path dir;
    private final boolean enabled;
    private final JsonMapper json;

    public LlmCache(StoryForgeProperties properties, JsonMapper json) {
        this.dir = Path.of(properties.dataDir()).resolve("cache").resolve("llm");
        this.enabled = properties.llm().cache();
        this.json = json;
    }

    public String key(LlmRequest request, int salt, List<String> route) {
        String material = String.join("\u0000", String.join(",", route), request.stage().name(),
                request.promptVersion(),
                String.valueOf(request.tier()), request.system(), request.user(), String.valueOf(salt));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<LlmResponse> get(String key) {
        Path file = dir.resolve(key + ".json");
        if (!enabled || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(json.readValue(file, LlmResponse.class));
    }

    public void put(String key, LlmResponse response) {
        if (!enabled) {
            return;
        }
        try {
            Files.createDirectories(dir);
            json.writeValue(dir.resolve(key + ".json"), response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
