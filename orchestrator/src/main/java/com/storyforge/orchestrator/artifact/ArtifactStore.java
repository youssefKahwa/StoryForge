package com.storyforge.orchestrator.artifact;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.stage.Stage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ArtifactStore {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,120}");

    private final Path runsDir;
    private final JsonMapper json;

    public ArtifactStore(StoryForgeProperties properties, JsonMapper json) {
        this.runsDir = Path.of(properties.dataDir()).toAbsolutePath().normalize().resolve("runs");
        this.json = json;
    }

    public RunFiles forRun(long runId) {
        return new RunFiles(runsDir.resolve(String.valueOf(runId)), json);
    }

    public Optional<Path> resolve(long runId, String stageFolder, String name) {
        if (Stage.fromFolder(stageFolder).isEmpty() || !SAFE_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path runDir = runsDir.resolve(String.valueOf(runId));
        Path file = runDir.resolve(stageFolder).resolve(name).normalize();
        return file.startsWith(runDir) && Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    public List<StoredFile> list(long runId) {
        Path runDir = runsDir.resolve(String.valueOf(runId));
        List<StoredFile> files = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            Path dir = runDir.resolve(stage.folder());
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> entries = Files.list(dir)) {
                entries.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(Path::getFileName))
                        .forEach(file -> files.add(new StoredFile(stage.folder(), file.getFileName().toString(),
                                size(file))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return files;
    }

    public void delete(long runId, Collection<Stage> stages) {
        Path runDir = runsDir.resolve(String.valueOf(runId));
        stages.forEach(stage -> deleteTree(runDir.resolve(stage.folder())));
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }
}
