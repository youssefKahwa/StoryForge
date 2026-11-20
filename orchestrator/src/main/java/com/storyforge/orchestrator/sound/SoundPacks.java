package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.model.LicenseRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sound packs the owner downloaded (Earshot SFX, ...) and unzipped into {@code library/sfx-packs/<pack>/}. A pack is
 * used only once its folder holds a {@code pack.json} with {@code "approved": true} and the licence summary, written
 * after its own licence file was read: nothing unlicensed reaches a monetized video. Files are found by the words in
 * their folder and file names; each is converted to WAV and measured the first time it is picked.
 */
class SoundPacks {

    /** pack.json, written by hand after reading the pack's licence. */
    record PackInfo(boolean approved, String name, String license, String credit) {
    }

    /** One file of a pack. wav, peak and levels are filled the first time it is used. */
    record Entry(String pack, String source, String name, List<String> keywords, double duration, String wav,
                 double peak, double meanDb, double maxDb) {
    }

    private static final Logger log = LoggerFactory.getLogger(SoundPacks.class);
    private static final Set<String> AUDIO = Set.of("wav", "aif", "aiff", "mp3", "flac", "ogg");
    private static final Set<String> NOISE = Set.of("sfx", "fx", "wav", "aif", "aiff", "mp3", "the", "and", "with",
            "take", "version", "mono", "stereo", "var", "variation", "edit", "mix", "final", "sound", "sounds",
            "effect", "effects", "free", "pack", "earshot");
    /** Words a category's sounds are named with in packs, beyond its Freesound queries. */
    static final Map<String, List<String>> WORDS = Map.ofEntries(
            Map.entry("whoosh", List.of("whoosh", "swoosh", "woosh", "swish", "swipe", "flyby", "passby")),
            Map.entry("whoosh_fast", List.of("whip", "swish", "swipe", "whoosh", "swoosh")),
            Map.entry("swoosh_soft", List.of("swoosh", "whoosh", "woosh", "air")),
            Map.entry("impact", List.of("impact", "hit", "punch", "thud", "slam", "smash", "crack", "knock")),
            Map.entry("boom", List.of("boom", "blast", "explosion", "explode")),
            Map.entry("sub_drop", List.of("subdrop", "drop", "bass")),
            Map.entry("riser", List.of("riser", "rise", "rising", "swell", "build")),
            Map.entry("reverse", List.of("reverse", "reversed")),
            Map.entry("braam", List.of("braam", "brass", "horn")),
            Map.entry("shutter", List.of("shutter", "camera")),
            Map.entry("glitch", List.of("glitch", "digital", "distort", "distortion", "zap", "static")),
            Map.entry("film_burn", List.of("projector", "film")),
            Map.entry("click", List.of("click", "tick", "button")),
            Map.entry("bell", List.of("bell", "chime", "ding")),
            Map.entry("pop", List.of("pop", "bubble", "pluck")),
            Map.entry("chair", List.of("creak", "chair", "cloth", "rustle")));
    private static final int SHORTLIST = 12;

    private final Path packs;
    private final Path cache;
    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final Function<Path, double[]> measure;
    private Map<String, PackInfo> infos;
    private List<Entry> entries;

    /** measure: a file -> {peak seconds, mean dB, max dB}. */
    SoundPacks(Path packs, Path cache, FfmpegService ffmpeg, JsonMapper json, Function<Path, double[]> measure) {
        this.packs = packs;
        this.cache = cache;
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.measure = measure;
    }

    /** A pack sound for a bank category, within its length range; empty when no approved pack has one. */
    synchronized Optional<SoundBank.Sound> pick(String category, List<String> queries, double minSeconds,
                                                double maxSeconds, long seed) {
        List<String> words = new ArrayList<>(WORDS.getOrDefault(category, List.of()));
        queries.forEach(q -> words.addAll(keywords(q)));
        return choose(category, rank(entries(), words, minSeconds, maxSeconds), seed);
    }

    /** A pack sound for a free-text script cue ("copier hum"): the files sharing the most words with it. */
    synchronized Optional<SoundBank.Sound> cue(String text, double minSeconds, double maxSeconds, long seed) {
        List<String> words = keywords(text).stream().filter(w -> w.length() >= 4).toList();
        return words.isEmpty() ? Optional.empty()
                : choose("cue", rank(entries(), words, minSeconds, maxSeconds), seed);
    }

    /** The licence of a pack file (its converted WAV), if it belongs to a pack. */
    synchronized Optional<LicenseRecord> license(String file) {
        return entries().stream().filter(e -> file.equals(e.wav())).findFirst()
                .map(e -> license(e, infos().get(e.pack())));
    }

    static LicenseRecord license(Entry entry, PackInfo info) {
        String summary = info == null || info.license() == null ? "royalty-free sound pack" : info.license();
        String credit = info == null || info.credit() == null ? entry.pack() : info.credit();
        return new LicenseRecord("royalty_free", credit, null,
                "\"" + entry.name() + "\" from " + credit + ": " + summary, null);
    }

    /** The files whose words match, best first: the most matching words, then the length nearest the middle. */
    static List<Entry> rank(List<Entry> all, List<String> words, double minSeconds, double maxSeconds) {
        Set<String> wanted = new LinkedHashSet<>(words);
        double middle = (minSeconds + maxSeconds) / 2;
        return all.stream()
                .filter(e -> e.duration() >= minSeconds && e.duration() <= maxSeconds)
                .filter(e -> e.keywords().stream().anyMatch(wanted::contains))
                .sorted(Comparator.comparingLong((Entry e) -> -e.keywords().stream().filter(wanted::contains).count())
                        .thenComparingDouble(e -> Math.abs(e.duration() - middle)))
                .limit(SHORTLIST).toList();
    }

    /** The words in a pack file's folders and name: "Whooshes/Whoosh_Air_Fast 03.aif" -> whoosh(es), air, fast. */
    static List<String> keywords(String path) {
        String spaced = path.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        Set<String> out = new LinkedHashSet<>();
        for (String word : spaced.split("[^a-z]+")) {
            if (word.length() < 3 || NOISE.contains(word)) {
                continue;
            }
            out.add(word);
            if (word.endsWith("es") && word.length() > 5) {
                out.add(word.substring(0, word.length() - 2));
            }
            if (word.endsWith("s") && word.length() > 4) {
                out.add(word.substring(0, word.length() - 1));
            }
        }
        return List.copyOf(out);
    }

    private Optional<SoundBank.Sound> choose(String category, List<Entry> ranked, long seed) {
        for (int tries = 0; tries < ranked.size(); tries++) {
            Entry entry = ready(ranked.get((int) Math.floorMod(seed + tries, ranked.size())));
            if (entry != null) {
                return Optional.of(new SoundBank.Sound(category, entry.wav(), stableId(entry.source()), entry.name(),
                        entry.pack(), null, entry.duration(), entry.peak(), entry.meanDb(), entry.maxDb()));
            }
        }
        return Optional.empty();
    }

    /** The entry with its WAV and measurements, made once (null when the file cannot be read). */
    private Entry ready(Entry entry) {
        if (entry.wav() != null && Files.isRegularFile(Path.of(entry.wav()))) {
            return entry;
        }
        try {
            Path dir = cache.resolve(entry.pack());
            Files.createDirectories(dir);
            Path wav = dir.resolve(Long.toHexString(stableId(entry.source())) + ".wav");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", entry.source(), "-ar", "48000", "-ac", "2",
                    "-c:a", "pcm_s16le", wav.toString()));
            double[] m = measure.apply(wav);
            Entry done = new Entry(entry.pack(), entry.source(), entry.name(), entry.keywords(), entry.duration(),
                    wav.toString(), m[0], m[1], m[2]);
            entries.replaceAll(e -> e.source().equals(entry.source()) ? done : e);
            save(entry.pack());
            return done;
        } catch (IOException | RuntimeException e) {
            log.warn("Pack sound {} not usable: {}", entry.source(), e.getMessage());
            return null;
        }
    }

    private static long stableId(String source) {
        return -Math.abs((long) source.hashCode() * 31 + source.length());
    }

    private Map<String, PackInfo> infos() {
        if (infos == null) {
            infos = new HashMap<>();
            if (Files.isDirectory(packs)) {
                try (Stream<Path> dirs = Files.list(packs)) {
                    dirs.filter(Files::isDirectory).forEach(dir -> {
                        Path file = dir.resolve("pack.json");
                        if (Files.isRegularFile(file)) {
                            try {
                                PackInfo info = json.readValue(file.toFile(), PackInfo.class);
                                if (info.approved()) {
                                    infos.put(dir.getFileName().toString(), info);
                                }
                            } catch (RuntimeException e) {
                                log.warn("Sound pack {}: pack.json unreadable: {}", dir.getFileName(), e.getMessage());
                            }
                        } else {
                            log.info("Sound pack {} not used yet: it has no approved pack.json (licence not checked)",
                                    dir.getFileName());
                        }
                    });
                } catch (IOException e) {
                    log.warn("Sound packs not read: {}", e.getMessage());
                }
            }
        }
        return infos;
    }

    /** Every file of the approved packs, from each pack's saved index or a first scan. */
    private List<Entry> entries() {
        if (entries == null) {
            entries = new ArrayList<>();
            for (String pack : infos().keySet()) {
                entries.addAll(load(pack));
            }
            if (!entries.isEmpty()) {
                log.info("Sound packs: {} file(s) from {}", entries.size(), infos().keySet());
            }
        }
        return entries;
    }

    private List<Entry> load(String pack) {
        Path index = cache.resolve(pack).resolve("pack-index.json");
        if (Files.isRegularFile(index)) {
            try {
                return json.readValue(index.toFile(), new TypeReference<List<Entry>>() { });
            } catch (RuntimeException e) {
                log.warn("Sound pack {} index unreadable, scanning again: {}", pack, e.getMessage());
            }
        }
        Path dir = packs.resolve(pack);
        List<Entry> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dir)) {
            List<Path> audio = files.filter(Files::isRegularFile).filter(SoundPacks::isAudio).toList();
            log.info("Sound pack {}: indexing {} file(s) once", pack, audio.size());
            for (Path file : audio) {
                String relative = dir.relativize(file).toString().replace('\\', '/');
                double duration;
                try {
                    duration = ffmpeg.probe(file).durationSec();
                } catch (RuntimeException e) {
                    continue;
                }
                String name = file.getFileName().toString().replaceAll("\\.[^.]+$", "");
                found.add(new Entry(pack, file.toString(), name, keywords(relative.replaceAll("\\.[^.]+$", "")),
                        duration, null, 0, -20, -3));
            }
        } catch (IOException e) {
            log.warn("Sound pack {} not scanned: {}", pack, e.getMessage());
            return List.of();
        }
        writeIndex(pack, found);
        return found;
    }

    private void save(String pack) {
        writeIndex(pack, entries.stream().filter(e -> e.pack().equals(pack)).collect(Collectors.toList()));
    }

    private void writeIndex(String pack, List<Entry> list) {
        try {
            Path dir = cache.resolve(pack);
            Files.createDirectories(dir);
            json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("pack-index.json").toFile(), list);
        } catch (IOException | RuntimeException e) {
            log.warn("Sound pack {} index not saved: {}", pack, e.getMessage());
        }
    }

    private static boolean isAudio(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && AUDIO.contains(name.substring(dot + 1));
    }
}
