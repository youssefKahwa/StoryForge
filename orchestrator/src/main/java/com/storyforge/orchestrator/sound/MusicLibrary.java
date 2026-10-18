package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.LicenseRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MusicLibrary {

    public static final List<String> MOODS = List.of("dread", "tension", "mystery", "calm", "hope", "sad", "epic");
    private static final Set<String> EXTENSIONS = Set.of("mp3", "wav", "ogg", "m4a", "flac");
    private static final Map<String, List<String>> FALLBACKS = Map.of(
            "dread", List.of("tension", "mystery", "sad"),
            "tension", List.of("dread", "mystery", "epic"),
            "mystery", List.of("tension", "calm", "dread"),
            "calm", List.of("hope", "mystery", "sad"),
            "hope", List.of("calm", "epic", "mystery"),
            "sad", List.of("calm", "dread", "mystery"),
            "epic", List.of("hope", "tension", "mystery"));
    private static final Map<String, String> SYNONYMS = synonyms();

    public record Track(String mood, Path file, String relativeName, LicenseRecord license) {
    }

    private record LicenseFile(LicenseRecord defaultLicense, Map<String, LicenseRecord> files) {
    }

    private final Path root;
    private final LicenseRecord defaultLicense;
    private final JsonMapper json;

    public MusicLibrary(StoryForgeProperties properties, JsonMapper json) {
        StoryForgeProperties.Sound sound = properties.sound();
        this.root = Path.of(sound.libraryDir()).toAbsolutePath().resolve("music");
        this.defaultLicense = sound.defaultMusicLicense();
        this.json = json;
    }

    public Path root() {
        return root;
    }

    public static String canonicalMood(String mood) {
        if (mood == null || mood.isBlank()) {
            return null;
        }
        String key = mood.toLowerCase(Locale.ROOT).strip();
        if (MOODS.contains(key)) {
            return key;
        }
        return SYNONYMS.entrySet().stream().filter(entry -> key.contains(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse("mystery");
    }

    public Map<String, List<Track>> scan() {
        Map<String, List<Track>> tracks = new LinkedHashMap<>();
        LicenseFile licenses = licenses();
        for (String mood : MOODS) {
            Path dir = root.resolve(mood);
            List<Track> list = new ArrayList<>();
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(Files::isRegularFile).filter(MusicLibrary::isAudio).sorted().forEach(file -> {
                        String name = mood + "/" + file.getFileName();
                        LicenseRecord license = licenses.files() != null && licenses.files().containsKey(name)
                                ? licenses.files().get(name)
                                : licenses.defaultLicense() != null ? licenses.defaultLicense() : defaultLicense;
                        list.add(new Track(mood, file, name, license));
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            tracks.put(mood, list);
        }
        return tracks;
    }

    public static List<String> fallbacks(String mood) {
        return FALLBACKS.getOrDefault(mood, List.of());
    }

    private LicenseFile licenses() {
        Path file = root.resolve("licenses.json");
        if (!Files.isRegularFile(file)) {
            return new LicenseFile(null, Map.of());
        }
        return json.readValue(file, new TypeReference<LicenseFile>() { });
    }

    private static boolean isAudio(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static Map<String, String> synonyms() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String word : List.of("ominous", "dark", "fear", "horror", "menac", "grim")) {
            map.put(word, "dread");
        }
        for (String word : List.of("sad", "grief", "somber", "sombre", "melanchol", "loss", "mourn", "tragic")) {
            map.put(word, "sad");
        }
        for (String word : List.of("suspense", "tense", "urgent", "chase", "danger", "intense", "anxious")) {
            map.put(word, "tension");
        }
        for (String word : List.of("epic", "triumph", "heroic", "climax", "grand", "battle")) {
            map.put(word, "epic");
        }
        for (String word : List.of("hope", "warm", "uplift", "resolve", "relief", "inspir")) {
            map.put(word, "hope");
        }
        for (String word : List.of("curious", "myster", "intrigue", "investigat", "eerie", "wonder")) {
            map.put(word, "mystery");
        }
        for (String word : List.of("reflect", "calm", "peace", "quiet", "neutral", "gentle", "nostalg")) {
            map.put(word, "calm");
        }
        return map;
    }
}
