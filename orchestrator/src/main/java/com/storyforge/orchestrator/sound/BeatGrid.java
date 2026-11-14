package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.sound.SoundPlan.MusicSegment;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where the beats of a music track are, so footage can cut on them as editors do. Onset strength from spectral flux,
 * then the grid (70-180 BPM, leaning toward 120) whose beats land on the strongest onsets over the whole track, refined
 * to the exact period and phase. A track without a steady pulse (an ambient bed) has no grid, and then nothing moves.
 * Cached per track.
 */
@Component
public class BeatGrid {

    /** bpm, period and phase in seconds, how clearly the onsets fall on the grid, the track length, every beat. */
    public record Beats(double bpm, double period, double phase, double confidence, double length,
                        List<Double> times) {
    }

    private static final Logger log = LoggerFactory.getLogger(BeatGrid.class);
    static final int RATE = 22_050;
    static final int FRAME = 1024;
    static final int HOP = 256;
    static final double FRAME_RATE = (double) RATE / HOP;
    static final double LATENCY = 0.75 * FRAME / HOP;
    static final double MIN_BPM = 70;
    static final double MAX_BPM = 180;
    /**
     * Onsets on the grid stand this many standard deviations above the track's average: a pulse you can cut to.
     * Measured on the music library: tracks with a clear pulse score 2.5-3.9, ambient beds 0.3-0.8, and the
     * in-between ones (1.0-1.3, often a guessed 120 BPM) are not trusted.
     */
    static final double MIN_CONFIDENCE = 2.0;
    private static final int MAX_BIN = FRAME * 8000 / RATE;
    private static final int VERSION = 1;

    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final Path cache;

    public BeatGrid(FfmpegService ffmpeg, JsonMapper json, StoryForgeProperties properties) {
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.cache = Path.of(properties.dataDir()).toAbsolutePath().resolve("library").resolve("music-beats");
    }

    /** The beats of a track when it has a steady pulse. */
    public Optional<Beats> of(Path track) {
        try {
            Files.createDirectories(cache);
            Path file = cache.resolve(key(track) + ".json");
            Beats beats;
            if (Files.isRegularFile(file)) {
                beats = json.readValue(file.toFile(), Beats.class);
            } else {
                Path raw = Files.createTempFile("storyforge-beats", ".f32");
                try {
                    ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", track.toString(), "-ac", "1", "-ar",
                            String.valueOf(RATE), "-f", "f32le", raw.toString()));
                    float[] samples = floats(Files.readAllBytes(raw));
                    beats = analyse(onsets(samples), samples.length / (double) RATE);
                } finally {
                    Files.deleteIfExists(raw);
                }
                json.writeValue(file.toFile(), beats);
                log.info("Beats of {}: {} BPM, confidence {}", track.getFileName(), Math.round(beats.bpm()),
                        Math.round(beats.confidence() * 100) / 100.0);
            }
            return beats.confidence() >= MIN_CONFIDENCE ? Optional.of(beats) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            log.warn("No beat grid for {}: {}", track.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Every beat in video time of the music the mix will play: each track starts at its segment's start and loops
     * from its beginning. Beats inside the fades between tracks are left out (two pulses at once).
     */
    public List<Double> videoBeats(List<MusicSegment> segments) {
        List<Double> out = new ArrayList<>();
        for (MusicSegment segment : segments) {
            Optional<Beats> beats = of(Path.of(segment.file()));
            if (beats.isEmpty() || beats.get().length() <= 0) {
                continue;
            }
            double fade = Math.min(SoundDesigner.CROSSFADE, segment.length() / 3);
            out.addAll(place(beats.get(), segment.start(), segment.end(), fade));
        }
        Collections.sort(out);
        return out;
    }

    static List<Double> place(Beats beats, double start, double end, double fade) {
        List<Double> out = new ArrayList<>();
        for (double loop = start; loop < end; loop += beats.length()) {
            for (double beat : beats.times()) {
                double t = loop + beat;
                if (t >= start + fade && t <= end - fade) {
                    out.add(Math.round(t * 1000) / 1000.0);
                }
            }
        }
        return out;
    }

    /** The beat nearest to {@code t} within {@code tolerance}, or null. {@code beats} must be sorted. */
    public static Double nearest(List<Double> beats, double t, double tolerance) {
        int index = Collections.binarySearch(beats, t);
        int at = index >= 0 ? index : -index - 1;
        Double best = null;
        for (int i = Math.max(0, at - 1); i <= Math.min(beats.size() - 1, at); i++) {
            double beat = beats.get(i);
            if (Math.abs(beat - t) <= tolerance && (best == null || Math.abs(beat - t) < Math.abs(best - t))) {
                best = beat;
            }
        }
        return best;
    }

    /** Onset strength per hop: how much louder the spectrum got (log magnitude, rising bins only), minus its trend. */
    static double[] onsets(float[] samples) {
        int frames = Math.max(0, (samples.length - FRAME) / HOP + 1);
        double[] window = new double[FRAME];
        for (int i = 0; i < FRAME; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / FRAME);
        }
        double[] flux = new double[frames];
        double[] previous = new double[MAX_BIN];
        double[] re = new double[FRAME];
        double[] im = new double[FRAME];
        for (int f = 0; f < frames; f++) {
            for (int i = 0; i < FRAME; i++) {
                re[i] = samples[f * HOP + i] * window[i];
                im[i] = 0;
            }
            fft(re, im);
            double sum = 0;
            for (int k = 1; k < MAX_BIN; k++) {
                double magnitude = Math.log1p(100 * Math.hypot(re[k], im[k]));
                if (f > 0) {
                    sum += Math.max(0, magnitude - previous[k]);
                }
                previous[k] = magnitude;
            }
            flux[f] = sum;
        }
        // Remove the slow trend (a crescendo is not a beat) and keep what rises above it.
        int half = (int) Math.round(FRAME_RATE * 0.25);
        double[] out = new double[frames];
        double running = 0;
        int count = 0;
        int lo = 0;
        int hi = -1;
        for (int f = 0; f < frames; f++) {
            while (hi < Math.min(frames - 1, f + half)) {
                running += flux[++hi];
                count++;
            }
            while (lo < f - half) {
                running -= flux[lo++];
                count--;
            }
            out[f] = Math.max(0, flux[f] - running / Math.max(1, count));
        }
        return out;
    }

    /** Tempo, period and phase of the onset curve, with how clearly the onsets sit on that grid. */
    static Beats analyse(double[] onset, double length) {
        int n = onset.length;
        double mean = 0;
        for (double v : onset) {
            mean += v;
        }
        mean /= Math.max(1, n);
        double variance = 0;
        for (double v : onset) {
            variance += (v - mean) * (v - mean);
        }
        double std = Math.sqrt(variance / Math.max(1, n));
        if (n < FRAME_RATE * 8 || std <= 1e-9) {
            return new Beats(0, 0, 0, 0, length, List.of());
        }
        // Tempo: every grid from 70 to 180 BPM is laid over the onsets. A grid twice too fast puts every other beat
        // on silence and scores lower by itself; between a tempo and its half, the lean toward 120 BPM decides.
        double coarse = 60 / 120.0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double bpm = MIN_BPM; bpm <= MAX_BPM; bpm += 1) {
            double p = 60 / bpm;
            double prior = Math.exp(-0.5 * Math.pow(Math.log(bpm / 120) / Math.log(2), 2));
            for (double phi = 0; phi < p; phi += 0.01) {
                double score = prior * (comb(onset, p, phi) - mean);
                if (score > bestScore) {
                    bestScore = score;
                    coarse = p;
                }
            }
        }
        // Exact period and phase: the grid whose beats land on the strongest onsets across the track.
        double period = coarse;
        double phase = 0;
        double best = Double.NEGATIVE_INFINITY;
        for (double p = coarse * 0.99; p <= coarse * 1.01; p += 0.0002) {
            for (double phi = 0; phi < p; phi += 0.005) {
                double score = comb(onset, p, phi);
                if (score > best) {
                    best = score;
                    period = p;
                    phase = phi;
                }
            }
        }
        List<Double> times = new ArrayList<>();
        for (double t = phase; t < length; t += period) {
            times.add(Math.round(t * 1000) / 1000.0);
        }
        return new Beats(60 / period, period, phase, (best - mean) / std, length, times);
    }

    /**
     * Mean onset strength at every beat of the grid (linear interpolation between hops). A hit enters the analysis
     * window at its end; the window's rise is steepest three quarters of the way in, so that is where the flux of a hit
     * peaks: hop f stands for time (f + LATENCY) / FRAME_RATE.
     */
    private static double comb(double[] onset, double period, double phase) {
        double sum = 0;
        int count = 0;
        for (double t = phase; t * FRAME_RATE - LATENCY < onset.length - 1; t += period) {
            double x = t * FRAME_RATE - LATENCY;
            if (x < 0) {
                continue;
            }
            int i = (int) x;
            double frac = x - i;
            sum += onset[i] * (1 - frac) + onset[i + 1] * frac;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    /** In-place radix-2 FFT; the length must be a power of two. */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2 * Math.PI / len;
            double wr = Math.cos(angle);
            double wi = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double cr = 1;
                double ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    double next = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = next;
                }
            }
        }
    }

    private static float[] floats(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[bytes.length / 4];
        for (int i = 0; i < out.length; i++) {
            out[i] = buffer.getFloat();
        }
        return out;
    }

    private static String key(Path track) throws IOException {
        String id = VERSION + "|" + track.toAbsolutePath() + "|" + Files.size(track) + "|"
                + Files.getLastModifiedTime(track).toMillis();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(id.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
