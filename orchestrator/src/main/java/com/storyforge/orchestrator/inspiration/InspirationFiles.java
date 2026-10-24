package com.storyforge.orchestrator.inspiration;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.model.VideoTranscript;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class InspirationFiles {

    private static final String TRANSCRIPTS = "transcripts.json";
    private static final String IDEAS = "ideas.json";

    private final Path root;
    private final JsonMapper json;

    public InspirationFiles(StoryForgeProperties properties, JsonMapper json) {
        this.root = Path.of(properties.dataDir()).toAbsolutePath().resolve("inspirations");
        this.json = json;
    }

    public void saveTranscripts(long id, List<VideoTranscript> transcripts) {
        write(id, TRANSCRIPTS, transcripts);
    }

    public List<VideoTranscript> transcripts(long id) {
        Path file = dir(id).resolve(TRANSCRIPTS);
        return Files.isRegularFile(file) ? json.readValue(file, new TypeReference<List<VideoTranscript>>() { })
                : List.of();
    }

    /**
     * A transcript this PC already made for the same video (any earlier inspiration), newest first: reading an
     * 11-minute video again costs about 15 minutes of CPU transcription.
     */
    public Optional<VideoTranscript> known(String url) {
        if (url == null || !Files.isDirectory(root)) {
            return Optional.empty();
        }
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(dir -> dir.getFileName().toString().matches("[0-9]+"))
                    .sorted(Comparator.comparingLong((Path dir) -> Long.parseLong(dir.getFileName().toString()))
                            .reversed())
                    .map(dir -> dir.resolve(TRANSCRIPTS)).filter(Files::isRegularFile)
                    .flatMap(file -> {
                        try {
                            return json.readValue(file, new TypeReference<List<VideoTranscript>>() { }).stream();
                        } catch (RuntimeException e) {
                            return Stream.empty();
                        }
                    })
                    .filter(t -> t.transcript() != null && !t.transcript().isBlank())
                    .filter(t -> url.equals(t.url()) || t.videoId() != null && url.contains(t.videoId()))
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void saveIdeas(long id, InspirationIdeas ideas) {
        write(id, IDEAS, ideas);
    }

    public Optional<InspirationIdeas> ideas(long id) {
        Path file = dir(id).resolve(IDEAS);
        return Files.isRegularFile(file) ? Optional.of(json.readValue(file, InspirationIdeas.class)) : Optional.empty();
    }

    private void write(long id, String name, Object value) {
        try {
            Files.createDirectories(dir(id));
            json.writerWithDefaultPrettyPrinter().writeValue(dir(id).resolve(name), value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path dir(long id) {
        return root.resolve(String.valueOf(id));
    }
}
