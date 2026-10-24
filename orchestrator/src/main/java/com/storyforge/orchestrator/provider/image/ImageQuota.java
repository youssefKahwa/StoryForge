package com.storyforge.orchestrator.provider.image;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class ImageQuota {

    private final Path dir;
    private final int dailyLimit;

    public ImageQuota(StoryForgeProperties properties) {
        this.dir = Path.of(properties.dataDir()).toAbsolutePath().resolve("cache").resolve("images");
        this.dailyLimit = properties.cloudflare().dailyImageLimit();
    }

    public int dailyLimit() {
        return dailyLimit;
    }

    public synchronized int remaining() {
        return Math.max(0, dailyLimit - used());
    }

    public synchronized void recordCall() {
        write(used() + 1);
    }

    public synchronized void markExhausted() {
        write(dailyLimit);
    }

    private int used() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        try {
            return Integer.parseInt(Files.readString(file, StandardCharsets.UTF_8).strip());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    private void write(int count) {
        try {
            Files.createDirectories(dir);
            Files.writeString(file(), String.valueOf(count), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file() {
        return dir.resolve("quota-" + LocalDate.now(ZoneOffset.UTC) + ".txt");
    }
}
