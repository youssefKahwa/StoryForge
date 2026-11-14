package com.storyforge.orchestrator.provider.clips;

import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.MediaInfo;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a work's footage library really holds. The same moment appears in several trailers and scene uploads, so
 * counting entries does not stop repeats: this index groups every scene cut into
 * <ul>
 *   <li>{@code moment}: the same footage, in any source. The limit on repeats counts these.</li>
 *   <li>{@code family}: the same set-up (the same face in the same room). Only used to avoid sameness back to back.</li>
 * </ul>
 * Each moment keeps its best copy. Thresholds were calibrated on the Fight Club library: correlation of small grey
 * frames, in both directions, is robust to the brightness differences between copies and does not merge dark shots,
 * which plain differences do. Built once per work and cached as {@code moments.json} next to the videos.
 */
final class FootageIndex {

    /** One scene cut of one source video, with what the index knows about it. */
    record Segment(int tile, MediaSvcClient.Trailer source, double start, double end, int moment, int family,
                   double brightness, boolean soft, boolean best) {
        double length() {
            return end - start;
        }

        String key() {
            return source.videoId() + "@" + fmt(start);
        }

        boolean dark() {
            return brightness < DARK;
        }

        boolean clean() {
            return !dark() && !soft;
        }
    }

    record Entry(String videoId, double start, double end, int moment, int family, double brightness, boolean soft,
                 boolean best) {
    }

    record Stored(int version, List<String> sources, List<Entry> entries) {
    }

    /** Unit-length frames of a segment, and the three frames that speak for it. */
    record Signature(float[][] frames, float[][] samples) {
    }

    private record Candidate(MediaSvcClient.Trailer source, double start, double end, Signature signature,
                             double brightness, double detail, int width) {
        double length() {
            return end - start;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(FootageIndex.class);
    private static final Pattern PTS = Pattern.compile("pts_time:([0-9.]+)");
    private static final Pattern CROP = Pattern.compile("crop=([0-9]+:[0-9]+:[0-9]+:[0-9]+)");
    static final String FILM_PREFIX = "film-";
    private static final int VERSION = 1;
    private static final int W = 64;
    private static final int H = 36;
    private static final int SMALL_W = 16;
    private static final int SMALL_H = 9;
    private static final int SMALL = SMALL_W * SMALL_H;
    private static final int PROMO_FPS = 4;
    private static final int FILM_FPS = 2;
    /** Same footage. At 0.93 true copies group and different shots of a dark room stay apart. */
    static final double MOMENT_CORRELATION = 0.93;
    /** Same set-up: the same character in the same room, another take or another line. */
    static final double FAMILY_CORRELATION = 0.8;
    /** A frame this flat matches nothing: flat frames all look alike. */
    private static final float FLAT_NORM = 60;
    private static final double HIDDEN_CUT_DIFF = 28;
    private static final double HIDDEN_CUT_LUMA = 25;
    private static final double MIN_CUT_GAP = 0.4;
    /** Below this average brightness (0-255) a moment reads as dark on a phone: about the darkest fifth of the footage. */
    static final double DARK = 20;
    /** A source narrower than this is a low-quality copy; used only when no other copy exists. */
    static final int SOFT_WIDTH = 800;
    private static final int SHARP_WIDTH = 1200;
    private static final double PROMO_SKIP_HEAD = 4;
    private static final double PROMO_SKIP_TAIL = 6;
    private static final double FILM_SKIP_HEAD = 60;
    private static final double FILM_SKIP_TAIL = 420;
    private static final double MIN_SEGMENT = 1.2;
    /** A longer "segment" usually hides a cut the detector missed in dark footage. */
    static final double MAX_SEGMENT = 9;
    private static final int TILES_PER_PROMO = 200;
    private static final int TILES_PER_FILM = 220;
    private static final int MAX_TILES = 800;

    private final FfmpegService ffmpeg;
    private final JsonMapper json;

    FootageIndex(FfmpegService ffmpeg, JsonMapper json) {
        this.ffmpeg = ffmpeg;
        this.json = json;
    }

    /** Every usable scene cut of these sources, in source order; re-grouped only when the sources change. */
    List<Segment> segments(List<MediaSvcClient.Trailer> sources, Path dir) throws IOException {
        Path file = dir.resolve("moments.json");
        List<String> ids = sources.stream().map(MediaSvcClient.Trailer::videoId).toList();
        if (Files.isRegularFile(file)) {
            try {
                Stored stored = json.readValue(file.toFile(), Stored.class);
                if (stored.version() == VERSION && ids.equals(stored.sources())) {
                    return restore(stored, sources);
                }
            } catch (RuntimeException e) {
                log.warn("Footage index {} unreadable, building it again: {}", file, e.getMessage());
            }
        }
        Stored stored = build(sources, dir, ids);
        json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), stored);
        return restore(stored, sources);
    }

    private static List<Segment> restore(Stored stored, List<MediaSvcClient.Trailer> sources) {
        Map<String, MediaSvcClient.Trailer> byId = sources.stream()
                .collect(Collectors.toMap(MediaSvcClient.Trailer::videoId, s -> s, (a, b) -> a));
        List<Segment> segments = new ArrayList<>();
        for (Entry entry : stored.entries()) {
            segments.add(new Segment(segments.size() + 1, byId.get(entry.videoId()), entry.start(), entry.end(),
                    entry.moment(), entry.family(), entry.brightness(), entry.soft(), entry.best()));
        }
        return segments;
    }

    private Stored build(List<MediaSvcClient.Trailer> sources, Path dir, List<String> ids) throws IOException {
        List<Candidate> candidates;
        try {
            candidates = sources.parallelStream().map(source -> candidates(source, dir)).toList().stream()
                    .flatMap(List::stream).limit(MAX_TILES).toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        List<Signature> signatures = candidates.stream().map(Candidate::signature).toList();
        double[] lengths = candidates.stream().mapToDouble(Candidate::length).toArray();
        int[] moments = group(signatures, lengths, MOMENT_CORRELATION);
        int count = Arrays.stream(moments).max().orElse(-1) + 1;
        int[] longest = new int[count];
        int[] best = new int[count];
        Arrays.fill(longest, -1);
        Arrays.fill(best, -1);
        for (int i = 0; i < moments.length; i++) {
            int moment = moments[i];
            if (longest[moment] < 0 || lengths[i] > lengths[longest[moment]]) {
                longest[moment] = i;
            }
            if (best[moment] < 0 || copyScore(candidates.get(i)) > copyScore(candidates.get(best[moment]))) {
                best[moment] = i;
            }
        }
        int[] families = group(Arrays.stream(longest).mapToObj(signatures::get).toList(),
                Arrays.stream(longest).mapToDouble(i -> lengths[i]).toArray(), FAMILY_CORRELATION);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < moments.length; i++) {
            Candidate candidate = candidates.get(i);
            entries.add(new Entry(candidate.source().videoId(), candidate.start(), candidate.end(), moments[i],
                    families[moments[i]], Math.round(candidate.brightness() * 10) / 10.0,
                    candidate.width() > 0 && candidate.width() < SOFT_WIDTH, best[moments[i]] == i));
        }
        double unique = Arrays.stream(best).mapToDouble(i -> lengths[i]).sum();
        log.info("Footage index: {} cuts are {} different moments, {} s of different footage, {} set-ups",
                entries.size(), count, Math.round(unique), Arrays.stream(families).max().orElse(-1) + 1);
        return new Stored(VERSION, ids, entries);
    }

    /** Longest and sharpest wins; resolution counts for a few seconds of length; a 640 px copy only when alone. */
    private static double copyScore(Candidate candidate) {
        return copyScore(candidate.width(), candidate.length(), candidate.detail(),
                !FilmClipFinder.watermark(candidate.source()).isEmpty());
    }

    static double copyScore(int width, double length, double detail, boolean watermarked) {
        double resolution = width > 0 && width < SOFT_WIDTH ? -100 : width >= SHARP_WIDTH ? 3 : 0;
        return resolution + Math.min(length, MAX_SEGMENT) * (watermarked ? 0.86 : 1) + Math.min(detail, 40) * 0.01;
    }

    private List<Candidate> candidates(MediaSvcClient.Trailer source, Path dir) {
        try {
            boolean film = source.videoId().startsWith(FILM_PREFIX);
            int fps = film ? FILM_FPS : PROMO_FPS;
            byte[] raw = frames(source, dir, fps);
            float[][] small = pooled(raw);
            List<Double> cuts = withHiddenCuts(cuts(source, dir), small, fps);
            double duration = source.durationSec() > 0 ? source.durationSec() : cuts.get(cuts.size() - 1) + 2;
            double head = film ? FILM_SKIP_HEAD : PROMO_SKIP_HEAD;
            double tail = film ? FILM_SKIP_TAIL : PROMO_SKIP_TAIL;
            List<double[]> ranges = new ArrayList<>();
            for (int i = 0; i + 1 < cuts.size(); i++) {
                double start = cuts.get(i);
                double end = Math.min(cuts.get(i + 1), duration);
                if (start >= head && end <= duration - tail && end - start >= MIN_SEGMENT
                        && end - start <= MAX_SEGMENT) {
                    ranges.add(new double[] {start, end});
                }
            }
            int limit = film ? TILES_PER_FILM : TILES_PER_PROMO;
            double step = Math.max(1, ranges.size() / (double) limit);
            int width = ffmpeg.probe(source.file()).width();
            List<Candidate> out = new ArrayList<>();
            for (double i = 0; i < ranges.size(); i += step) {
                double[] range = ranges.get((int) i);
                int first = (int) (range[0] * fps) + 1;
                int last = Math.min(small.length, (int) (range[1] * fps));
                if (last <= first) {
                    continue;
                }
                float[][] part = Arrays.copyOfRange(small, first, last);
                out.add(new Candidate(source, range[0], range[1], signature(part), brightness(part),
                        detail(raw, (first + last) / 2), width));
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One FFmpeg pass over the whole video, far faster than seeking per segment; kept next to the video. */
    private byte[] frames(MediaSvcClient.Trailer source, Path dir, int fps) throws IOException {
        Path file = dir.resolve(FilmClipFinder.slug(source.videoId()) + ".frames");
        if (!Files.isRegularFile(file)) {
            Path partial = file.resolveSibling(file.getFileName() + ".part");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", source.file().toString(), "-an", "-vf",
                    bars(source, dir) + "fps=" + fps + ",scale=" + W + ":" + H + ",format=gray", "-f", "rawvideo",
                    partial.toString()));
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return Files.readAllBytes(file);
    }

    /** Averages each 64x36 grey frame down to 16x9. */
    static float[][] pooled(byte[] raw) {
        int count = raw.length / (W * H);
        float[][] small = new float[count][SMALL];
        for (int f = 0; f < count; f++) {
            int base = f * W * H;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    small[f][(y / 4) * SMALL_W + x / 4] += (raw[base + y * W + x] & 0xFF) / 16f;
                }
            }
        }
        return small;
    }

    /**
     * The scene detector misses cuts in dark footage. A jump between two consecutive small frames with little change
     * in overall brightness is a cut it missed (a fade or a flash changes the brightness instead).
     */
    static List<Double> withHiddenCuts(List<Double> cuts, float[][] small, double fps) {
        TreeSet<Double> all = new TreeSet<>(cuts);
        for (int i = 1; i < small.length; i++) {
            double diff = 0;
            double luma = 0;
            for (int p = 0; p < SMALL; p++) {
                diff += Math.abs(small[i][p] - small[i - 1][p]);
                luma += small[i][p] - small[i - 1][p];
            }
            if (diff / SMALL > HIDDEN_CUT_DIFF && Math.abs(luma / SMALL) < HIDDEN_CUT_LUMA) {
                all.add(i / fps);
            }
        }
        List<Double> merged = new ArrayList<>();
        for (double cut : all) {
            if (merged.isEmpty() || cut - merged.get(merged.size() - 1) >= MIN_CUT_GAP) {
                merged.add(cut);
            }
        }
        return merged;
    }

    static Signature signature(float[][] part) {
        float[][] frames = Arrays.stream(part).map(FootageIndex::unit).toArray(float[][]::new);
        float[][] samples = new float[][] {frames[(int) (frames.length * 0.25)], frames[(int) (frames.length * 0.5)],
                frames[(int) (frames.length * 0.75)]};
        return new Signature(frames, samples);
    }

    /** Zero mean and unit length, so a brighter copy of the same frame still matches; flat frames become zero. */
    static float[] unit(float[] frame) {
        double mean = 0;
        for (float v : frame) {
            mean += v;
        }
        mean /= frame.length;
        double norm = 0;
        float[] out = new float[frame.length];
        for (int i = 0; i < frame.length; i++) {
            out[i] = (float) (frame[i] - mean);
            norm += out[i] * out[i];
        }
        norm = Math.sqrt(norm);
        if (norm <= FLAT_NORM) {
            return new float[frame.length];
        }
        for (int i = 0; i < out.length; i++) {
            out[i] /= (float) norm;
        }
        return out;
    }

    /** For each sample of a, its best correlation with any frame of b; the average over the samples. */
    static double correlation(Signature a, Signature b) {
        double total = 0;
        for (float[] sample : a.samples()) {
            double bestMatch = -1;
            for (float[] frame : b.frames()) {
                double dot = 0;
                for (int i = 0; i < sample.length; i++) {
                    dot += sample[i] * frame[i];
                }
                bestMatch = Math.max(bestMatch, dot);
            }
            total += bestMatch;
        }
        return total / a.samples().length;
    }

    /** Greedy grouping, longest first; the first member of a group speaks for it. Returns each item's group number. */
    static int[] group(List<Signature> signatures, double[] lengths, double threshold) {
        int[] groups = new int[signatures.size()];
        List<Integer> order = IntStream.range(0, signatures.size()).boxed()
                .sorted(Comparator.comparingDouble((Integer i) -> -lengths[i])).toList();
        List<Signature> leaders = new ArrayList<>();
        for (int i : order) {
            Signature signature = signatures.get(i);
            int found = -1;
            for (int g = 0; g < leaders.size() && found < 0; g++) {
                Signature leader = leaders.get(g);
                if (correlation(signature, leader) >= threshold && correlation(leader, signature) >= threshold) {
                    found = g;
                }
            }
            if (found < 0) {
                leaders.add(signature);
                found = leaders.size() - 1;
            }
            groups[i] = found;
        }
        return groups;
    }

    private static double brightness(float[][] part) {
        double sum = 0;
        for (float[] frame : part) {
            for (float v : frame) {
                sum += v;
            }
        }
        return sum / (part.length * SMALL);
    }

    /** Mean change between neighbouring pixels. Says which of two copies of one frame is sharper, not much more. */
    private static double detail(byte[] raw, int frame) {
        int base = Math.min(frame, raw.length / (W * H) - 1) * W * H;
        double sum = 0;
        for (int y = 0; y < H - 1; y++) {
            for (int x = 0; x < W - 1; x++) {
                int here = raw[base + y * W + x] & 0xFF;
                sum += Math.abs(here - (raw[base + y * W + x + 1] & 0xFF))
                        + Math.abs(here - (raw[base + (y + 1) * W + x] & 0xFF));
            }
        }
        return sum / ((W - 1) * (H - 1));
    }

    /**
     * A crop filter that removes black letterbox bars baked into the source (common in trailers), so the footage
     * really fills the screen; empty when there are none. Measured once per source on a few seconds of its middle.
     */
    String bars(MediaSvcClient.Trailer source, Path dir) {
        Path cache = dir.resolve(FilmClipFinder.slug(source.videoId()) + ".crop.txt");
        try {
            if (Files.isRegularFile(cache)) {
                return Files.readString(cache);
            }
            double middle = Math.max(0, source.durationSec() / 2 - 5);
            String output = ffmpeg.ffmpeg(List.of("-ss", fmt(middle), "-t", "10", "-i", source.file().toString(),
                    "-an", "-vf", "cropdetect=limit=24:round=2:reset=0", "-f", "null", "-"));
            Matcher matcher = CROP.matcher(output);
            String crop = null;
            while (matcher.find()) {
                crop = matcher.group(1);
            }
            MediaInfo info = ffmpeg.probe(source.file());
            String filter = "";
            if (crop != null && info.height() > 0) {
                String[] parts = crop.split(":");
                int h = Integer.parseInt(parts[1]);
                int w = Integer.parseInt(parts[0]);
                // Only real bars: at least 4% of the height removed, and the width kept.
                if (h < info.height() * 0.96 && h > info.height() * 0.5 && w >= info.width() * 0.96) {
                    filter = "crop=" + crop + ",";
                }
            }
            Files.writeString(cache, filter);
            return filter;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private List<Double> cuts(MediaSvcClient.Trailer source, Path dir) throws IOException {
        Path cache = dir.resolve(FilmClipFinder.slug(source.videoId()) + ".cuts.json");
        if (Files.isRegularFile(cache)) {
            return json.readValue(cache.toFile(), new TypeReference<List<Double>>() { });
        }
        String output = ffmpeg.ffmpeg(List.of("-i", source.file().toString(), "-an", "-vf",
                "select='gt(scene,0.18)',showinfo", "-f", "null", "-"));
        List<Double> cuts = new ArrayList<>(List.of(0.0));
        Matcher matcher = PTS.matcher(output);
        while (matcher.find()) {
            cuts.add(Double.parseDouble(matcher.group(1)));
        }
        cuts.add(source.durationSec() > 0 ? source.durationSec() : cuts.get(cuts.size() - 1) + 2);
        json.writeValue(cache.toFile(), cuts);
        return cuts;
    }

    /** Moment number to its segments, for callers that work per moment. */
    static Map<Integer, List<Segment>> byMoment(List<Segment> segments) {
        Map<Integer, List<Segment>> out = new HashMap<>();
        segments.forEach(s -> out.computeIfAbsent(s.moment(), m -> new ArrayList<>()).add(s));
        return out;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
