package com.storyforge.orchestrator.artifact;

import com.storyforge.orchestrator.stage.Stage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

public final class RunFiles {

    private final Path root;
    private final JsonMapper json;

    RunFiles(Path root, JsonMapper json) {
        this.root = root;
        this.json = json;
    }

    public Path root() {
        return root;
    }

    public Path dir(Stage stage) {
        Path dir = root.resolve(stage.folder());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    public Path path(Stage stage, String name) {
        return dir(stage).resolve(name);
    }

    public String relative(Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    public boolean exists(Stage stage, String name) {
        return Files.isRegularFile(root.resolve(stage.folder()).resolve(name));
    }

    public boolean hasAll(Stage stage, List<String> names) {
        return !names.isEmpty() && names.stream().allMatch(name -> exists(stage, name));
    }

    public <T> T read(Stage stage, String name, Class<T> type) {
        return json.readValue(requireFile(stage, name), type);
    }

    public <T> T read(Stage stage, String name, TypeReference<T> type) {
        return json.readValue(requireFile(stage, name), type);
    }

    public Path write(Stage stage, String name, Object value) {
        Path file = path(stage, name);
        json.writerWithDefaultPrettyPrinter().writeValue(file, value);
        return file;
    }

    /** Run-level file outside the stage folders (survives re-runs). */
    public <T> java.util.Optional<T> readRoot(String name, Class<T> type) {
        Path file = root.resolve(name);
        return Files.isRegularFile(file) ? java.util.Optional.of(json.readValue(file, type)) : java.util.Optional.empty();
    }

    public void writeRoot(String name, Object value) {
        try {
            Files.createDirectories(root.resolve(name).getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        json.writerWithDefaultPrettyPrinter().writeValue(root.resolve(name).toFile(), value);
    }

    /** Owner revisions for this run. Kept at the run root, so re-running stages never deletes them. */
    public RunOverrides overrides() {
        Path file = root.resolve(RunOverrides.FILE);
        return Files.isRegularFile(file) ? json.readValue(file, RunOverrides.class) : RunOverrides.empty();
    }

    public void writeOverrides(RunOverrides overrides) {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        json.writerWithDefaultPrettyPrinter().writeValue(root.resolve(RunOverrides.FILE).toFile(), overrides);
    }

    public String readText(Stage stage, String name) {
        try {
            return Files.readString(requireFile(stage, name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path writeText(Stage stage, String name, String text) {
        Path file = path(stage, name);
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    private Path requireFile(Stage stage, String name) {
        Path file = root.resolve(stage.folder()).resolve(name);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Missing artifact " + stage.folder() + "/" + name);
        }
        return file;
    }
}
