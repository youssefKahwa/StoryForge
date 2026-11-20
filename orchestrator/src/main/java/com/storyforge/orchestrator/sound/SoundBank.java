package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.model.LicenseRecord;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real, recorded sound effects instead of synthesized ones: CC0 sounds from Freesound, fetched once per category
 * (whoosh, impact, riser, ambience...) or per script cue ("projector whir") and kept in {@code library/sfx-bank}.
 * Each sound's loudest moment is measured once, so a hit or the end of a riser lands exactly on the cut.
 * Without a Freesound key the bank only uses what it already holds; callers fall back to synthesized presets.
 */
@Component
public class SoundBank {

    /** peak: seconds from the start of the file to its loudest moment; meanDb/maxDb: measured levels (dBFS). */
    public record Sound(String category, String file, long id, String name, String author, String url,
                        double duration, double peak, double meanDb, double maxDb) {

        /** Gain that brings this sound's loudest moment to {@code targetPeakDb}. */
        public double gainForPeak(double targetPeakDb) {
            return Math.max(-30, Math.min(18, targetPeakDb - maxDb));
        }

        /** Gain that brings this sound's average level to {@code targetMeanDb} (ambience beds). */
        public double gainForMean(double targetMeanDb) {
            return Math.max(-30, Math.min(24, targetMeanDb - meanDb));
        }


        public LicenseRecord license() {
            return new LicenseRecord("cc0", "Freesound: " + author, url,
                    "\"" + name + "\" by " + author + " on Freesound, Creative Commons 0 (public domain)", null);
        }
    }

    private record Category(List<String> queries, double minSeconds, double maxSeconds, int keep) {
    }

    private static final Logger log = LoggerFactory.getLogger(SoundBank.class);
    private static final String API = "https://freesound.org/apiv2/search/text/";
    private static final Pattern PEAK_TIME = Pattern.compile("pts_time:([0-9.]+)");
    private static final Pattern PEAK_LEVEL = Pattern.compile("Peak_level=(-?[0-9.]+|-inf)");
    private static final Pattern MEAN = Pattern.compile("mean_volume: *(-?[0-9.]+) dB");
    private static final Pattern MAX = Pattern.compile("max_volume: *(-?[0-9.]+) dB");
    private static final int CUE_KEEP = 2;
    private static final Map<String, Category> CATEGORIES = categories();

    private final Path root;
    private final String apiKey;
    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private Map<String, List<Sound>> index;
    private long lastCall;

    private final SoundPacks packs;

    public SoundBank(StoryForgeProperties properties, FfmpegService ffmpeg, JsonMapper json,
                     @Value("${FREESOUND_API_KEY:}") String apiKey) {
        Path library = Path.of(properties.sound().libraryDir()).toAbsolutePath();
        this.root = library.resolve("sfx-bank");
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.packs = new SoundPacks(library.resolve("sfx-packs"), root.resolve("packs"), ffmpeg, json, file -> {
            double[] levels = levels(file);
            return new double[] {peak(file), levels[0], levels[1]};
        });
    }

    public static boolean isCategory(String name) {
        return CATEGORIES.containsKey(name);
    }

    /** A sound of a known category; {@code seed} picks the variant so every cut does not sound the same. */
    public synchronized Optional<Sound> pick(String category, long seed) {
        Category spec = CATEGORIES.get(category);
        if (spec == null) {
            return Optional.empty();
        }
        // The owner's approved sound packs first (transitions, hits, UI sounds; never ambience beds).
        if (SoundPacks.WORDS.containsKey(category)) {
            Optional<Sound> fromPack = packs.pick(category, spec.queries(), spec.minSeconds(), spec.maxSeconds(), seed);
            if (fromPack.isPresent()) {
                return fromPack;
            }
        }
        List<Sound> sounds = ensure(category, spec);
        return sounds.isEmpty() ? Optional.empty() : Optional.of(sounds.get((int) Math.floorMod(seed, sounds.size())));
    }

    /** A specific bank sound (one the owner picked by ear), or any sound of its category when it is missing. */
    public synchronized Optional<Sound> find(String category, long id) {
        Optional<Sound> exact = index().getOrDefault(category, List.of()).stream()
                .filter(s -> s.id() == id && Files.isRegularFile(Path.of(s.file()))).findFirst();
        return exact.isPresent() ? exact : pick(category, id);
    }

    /** A sound for a free-text script cue such as "projector whir"; empty when nothing fits. */
    public synchronized Optional<Sound> cue(String text, long seed) {
        String words = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]+", " ")
                .replaceAll("\\s+", " ").strip();
        if (words.length() < 3) {
            return Optional.empty();
        }
        Optional<Sound> fromPack = packs.cue(words, 0.3, 8, seed);
        if (fromPack.isPresent()) {
            return fromPack;
        }
        String key = "cue_" + words.replace(' ', '_');
        List<Sound> sounds = ensure(key, new Category(List.of(words), 0.3, 8, CUE_KEEP));
        return sounds.isEmpty() ? Optional.empty() : Optional.of(sounds.get((int) Math.floorMod(seed, sounds.size())));
    }

    /** The licence of a bank file, if the file belongs to the bank. */
    public synchronized Optional<LicenseRecord> license(String file) {
        Optional<LicenseRecord> pack = packs.license(file);
        if (pack.isPresent()) {
            return pack;
        }
        return index().values().stream().flatMap(List::stream).filter(s -> s.file().equals(file)).findFirst()
                .map(Sound::license);
    }

    private List<Sound> ensure(String key, Category spec) {
        List<Sound> have = index().getOrDefault(key, List.of()).stream()
                .filter(s -> Files.isRegularFile(Path.of(s.file()))).toList();
        if (have.size() >= spec.keep() || apiKey.isEmpty()) {
            return have;
        }
        List<Sound> found = new ArrayList<>(have);
        for (String query : spec.queries()) {
            if (found.size() >= spec.keep()) {
                break;
            }
            try {
                fetch(key, query, spec, found);
            } catch (IOException | RuntimeException e) {
                log.warn("Freesound search '{}' failed: {}", query, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        index().put(key, found);
        save();
        if (found.size() > have.size()) {
            log.info("Sound bank: {} now has {} sound(s)", key, found.size());
        }
        return found;
    }

    private void fetch(String key, String query, Category spec, List<Sound> found)
            throws IOException, InterruptedException {
        String filter = "license:\"Creative Commons 0\" duration:[" + spec.minSeconds() + " TO " + spec.maxSeconds()
                + "]";
        String url = API + "?query=" + enc(query) + "&filter=" + enc(filter) + "&sort=rating_desc&page_size=20"
                + "&fields=id,name,username,license,duration,previews,url&token=" + enc(apiKey);
        throttle();
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        Path dir = root.resolve(key);
        Files.createDirectories(dir);
        for (JsonNode hit : json.readTree(response.body()).path("results")) {
            if (found.size() >= spec.keep()) {
                return;
            }
            long id = hit.path("id").asLong();
            if (found.stream().anyMatch(s -> s.id() == id)) {
                continue;
            }
            String preview = hit.path("previews").path("preview-hq-mp3").asString("");
            if (preview.isEmpty()) {
                continue;
            }
            Path file = dir.resolve(id + ".mp3");
            if (!Files.isRegularFile(file)) {
                HttpResponse<InputStream> audio = http.send(HttpRequest.newBuilder(URI.create(preview))
                        .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                if (audio.statusCode() != 200) {
                    continue;
                }
                Path part = dir.resolve(id + ".part");
                try (InputStream in = audio.body()) {
                    Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
            }
            double[] levels = levels(file);
            found.add(new Sound(key, file.toString(), id, hit.path("name").asString(""),
                    hit.path("username").asString(""), hit.path("url").asString(""),
                    hit.path("duration").asDouble(0), peak(file), levels[0], levels[1]));
        }
    }

    /** Seconds to the loudest 20 ms of the file. */
    double peak(Path file) {
        try {
            String output = ffmpeg.ffmpeg(List.of("-i", file.toString(), "-af",
                    "aresample=48000,asetnsamples=960,astats=metadata=1:reset=1,"
                            + "ametadata=print:key=lavfi.astats.Overall.Peak_level",
                    "-f", "null", "-"));
            double best = Double.NEGATIVE_INFINITY;
            double at = 0;
            double time = 0;
            for (String line : output.split("\\R")) {
                Matcher t = PEAK_TIME.matcher(line);
                if (t.find()) {
                    time = Double.parseDouble(t.group(1));
                }
                Matcher p = PEAK_LEVEL.matcher(line);
                if (p.find() && !p.group(1).equals("-inf")) {
                    double level = Double.parseDouble(p.group(1));
                    if (level > best) {
                        best = level;
                        at = time;
                    }
                }
            }
            return Math.round(at * 1000) / 1000.0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Mean and max volume in dBFS (FFmpeg volumedetect). */
    double[] levels(Path file) {
        try {
            String output = ffmpeg.ffmpeg(List.of("-i", file.toString(), "-af", "volumedetect", "-f", "null", "-"));
            Matcher mean = MEAN.matcher(output);
            Matcher max = MAX.matcher(output);
            return new double[] {mean.find() ? Double.parseDouble(mean.group(1)) : -20,
                    max.find() ? Double.parseDouble(max.group(1)) : -3};
        } catch (RuntimeException e) {
            return new double[] {-20, -3};
        }
    }

    private Map<String, List<Sound>> index() {
        if (index == null) {
            Path file = root.resolve("index.json");
            index = new LinkedHashMap<>();
            if (Files.isRegularFile(file)) {
                try {
                    index.putAll(json.readValue(file.toFile(), new TypeReference<Map<String, List<Sound>>>() { }));
                } catch (RuntimeException e) {
                    log.warn("Sound bank index unreadable, starting a new one: {}", e.getMessage());
                }
            }
        }
        return index;
    }

    private void save() {
        try {
            Files.createDirectories(root);
            json.writerWithDefaultPrettyPrinter().writeValue(root.resolve("index.json").toFile(), index());
        } catch (IOException | RuntimeException e) {
            log.warn("Sound bank index not saved: {}", e.getMessage());
        }
    }

    /** Freesound allows 60 requests a minute. */
    private void throttle() throws InterruptedException {
        long wait = lastCall + 1100 - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastCall = System.currentTimeMillis();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, Category> categories() {
        Map<String, Category> map = new LinkedHashMap<>();
        map.put("whoosh", new Category(List.of("whoosh", "swoosh transition"), 0.3, 2.5, 4));
        map.put("whoosh_fast", new Category(List.of("whip whoosh", "fast swish"), 0.15, 1.2, 4));
        map.put("swoosh_soft", new Category(List.of("soft whoosh", "air swoosh"), 0.3, 2.0, 3));
        map.put("impact", new Category(List.of("cinematic impact", "impact hit"), 0.5, 4, 4));
        map.put("boom", new Category(List.of("cinematic boom", "deep boom"), 1.5, 8, 3));
        map.put("sub_drop", new Category(List.of("sub drop", "bass drop"), 0.8, 6, 3));
        map.put("riser", new Category(List.of("riser", "tension riser"), 2, 8, 3));
        map.put("reverse", new Category(List.of("reverse cymbal", "reverse swell"), 1, 5, 3));
        map.put("braam", new Category(List.of("braam", "cinematic brass hit"), 1.5, 8, 2));
        map.put("shutter", new Category(List.of("camera shutter"), 0.1, 1.5, 3));
        map.put("glitch", new Category(List.of("glitch", "digital glitch"), 0.2, 2.5, 4));
        map.put("film_burn", new Category(List.of("film projector", "projector start"), 0.8, 6, 2));
        map.put("click", new Category(List.of("mouse click", "button click"), 0.05, 1.0, 3));
        map.put("bell", new Category(List.of("reward notification", "crystal bell"), 0.3, 3, 3));
        map.put("pop", new Category(List.of("bubble pop", "cartoon pop"), 0.05, 1.0, 3));
        map.put("chair", new Category(List.of("office chair creak", "cloth rustle"), 0.3, 3, 2));
        map.put("amb_room", new Category(List.of("room tone"), 20, 300, 3));
        map.put("amb_city", new Category(List.of("city ambience", "street traffic ambience"), 20, 300, 3));
        map.put("amb_rain", new Category(List.of("rain ambience"), 20, 300, 2));
        map.put("amb_night", new Category(List.of("night ambience"), 20, 300, 2));
        map.put("amb_crowd", new Category(List.of("crowd murmur", "crowd ambience"), 20, 300, 2));
        map.put("amb_office", new Category(List.of("office ambience"), 20, 300, 2));
        map.put("amb_wind", new Category(List.of("wind ambience"), 20, 300, 2));
        map.put("amb_bar", new Category(List.of("bar ambience", "pub ambience"), 20, 300, 2));
        map.put("amb_fire", new Category(List.of("fire crackling"), 20, 300, 2));
        map.put("amb_industrial", new Category(List.of("factory ambience", "industrial ambience"), 20, 300, 2));
        return map;
    }
}
