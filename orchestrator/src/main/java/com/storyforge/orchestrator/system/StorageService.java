package com.storyforge.orchestrator.system;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.run.Run;
import com.storyforge.orchestrator.run.RunRepository;
import com.storyforge.orchestrator.run.RunStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Disk usage and cleanup. Cleanup removes the heavy media of old completed runs (clips, images, renders) and keeps
 * their JSON (script, QA, kit) and the export folders, which hold their own hard links to the final files.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final Set<String> MEDIA = Set.of("wav", "mp3", "m4a", "flac", "png", "jpg", "jpeg", "webp", "mp4");

    public record Usage(long freeBytes, long runsBytes, long exportsBytes, long cleanableBytes, int cleanableRuns) {
    }

    public record Cleanup(int runsCleaned, long bytesFreed) {
    }

    private final Path dataDir;
    private final RunRepository runs;

    public StorageService(StoryForgeProperties properties, RunRepository runs) {
        this.dataDir = Path.of(properties.dataDir()).toAbsolutePath().normalize();
        this.runs = runs;
    }

    public long freeBytes() {
        try {
            Path existing = dataDir;
            while (existing != null && !Files.exists(existing)) {
                existing = existing.getParent();
            }
            return existing == null ? 0 : Files.getFileStore(existing).getUsableSpace();
        } catch (IOException e) {
            return 0;
        }
    }

    public Usage usage(int olderThanDays) {
        List<Run> candidates = candidates(olderThanDays);
        long cleanable = candidates.stream().mapToLong(run -> mediaBytes(runDir(run))).sum();
        int count = (int) candidates.stream().filter(run -> mediaBytes(runDir(run)) > 0).count();
        return new Usage(freeBytes(), size(dataDir.resolve("runs")), size(dataDir.resolve("exports")), cleanable,
                count);
    }

    public Cleanup cleanup(int olderThanDays) {
        int cleaned = 0;
        long freed = 0;
        for (Run run : candidates(olderThanDays)) {
            long bytes = deleteMedia(runDir(run));
            if (bytes > 0) {
                cleaned++;
                freed += bytes;
            }
        }
        log.info("Storage cleanup: {} runs, {} freed", cleaned, human(freed));
        return new Cleanup(cleaned, freed);
    }

    private List<Run> candidates(int olderThanDays) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(0, olderThanDays)));
        return runs.findByStatus(RunStatus.COMPLETED).stream()
                .filter(run -> {
                    Instant finished = run.getFinishedAt() != null ? run.getFinishedAt() : run.getUpdatedAt();
                    return finished != null && finished.isBefore(cutoff);
                })
                .toList();
    }

    private Path runDir(Run run) {
        return dataDir.resolve("runs").resolve(String.valueOf(run.getId()));
    }

    private static long mediaBytes(Path dir) {
        return walk(dir).filter(StorageService::isMedia).mapToLong(StorageService::fileSize).sum();
    }

    private static long deleteMedia(Path dir) {
        long freed = 0;
        for (Path file : walk(dir).filter(StorageService::isMedia).toList()) {
            long bytes = fileSize(file);
            try {
                Files.deleteIfExists(file);
                freed += bytes;
            } catch (IOException e) {
                log.warn("Could not delete {}: {}", file, e.getMessage());
            }
        }
        return freed;
    }

    private static long size(Path dir) {
        return walk(dir).mapToLong(StorageService::fileSize).sum();
    }

    private static Stream<Path> walk(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Stream.empty();
        }
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isMedia(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && MEDIA.contains(name.substring(dot + 1));
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }

    public static String human(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%d KB", bytes / 1024);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
