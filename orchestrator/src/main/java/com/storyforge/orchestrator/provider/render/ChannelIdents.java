package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.host.SubscribeNudge;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.MediaException;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import com.storyforge.orchestrator.sound.MusicLibrary;
import com.storyforge.orchestrator.sound.SoundBank;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The channel intro (two paths split, the logo appears, "BiPath") and outro (the paths meet, a big bang, the logo,
 * a subscribe click, the end screen), chosen by the owner (ChannelIdent.jsx: two_paths / paths). Each is made once
 * per channel voice: the voice lines (Kokoro), the animation (Remotion, silent), then the voice, the sounds (CC0
 * bank) and a music bed mixed by FFmpeg; the result is cached in the library. {@link #wrap} puts them around a
 * rendered video: the intro right after the opening hook, the outro at the end.
 */
@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "render", havingValue = "remotion")
public class ChannelIdents {

    /** In assemble: where the intro went and how long both are, to shift captions and chapters. */
    public static final String TIMELINE = "ident.json";

    public record Timeline(double introAt, double introSeconds, double outroSeconds) {
        /** A time of the content video in the final video. */
        public double shift(double seconds) {
            return seconds >= introAt ? seconds + introSeconds : seconds;
        }
    }

    /** Bump when the animations, lines or sounds change, so the cached files are made again. */
    static final String VERSION = "v5";
    private static final List<String> INTRO_LINES = List.of("Every story has two paths.", "This is BiPath.");
    private static final List<String> OUTRO_LINES = List.of("That's it for today.",
            "Tell me in the comments which path you would have taken.",
            "And if you want more stories like this, subscribe. See you on the next one.");
    private static final double INTRO_LEAD = 0.4;
    private static final double OUTRO_LEAD = 0.5;
    private static final int FPS = 30;
    /** The subscribe pill is clicked 66 frames after it starts (SubscribeNudge.jsx CLICK). */
    private static final double CLICK_AFTER = 2.2;
    private static final Pattern SILENCE = Pattern.compile("silence_start: ([0-9.]+)");
    private static final Logger log = LoggerFactory.getLogger(ChannelIdents.class);

    private record Cue(String category, long id, double at, double peakDb) {
    }

    private final RemotionRenderer renderer;
    private final MediaSvcClient media;
    private final SoundBank bank;
    private final MusicLibrary music;
    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final SubscribeNudge subscribe;
    private final Path cache;
    private final boolean enabled;

    public ChannelIdents(RemotionRenderer renderer, MediaSvcClient media, SoundBank bank, MusicLibrary music,
                         FfmpegService ffmpeg, JsonMapper json, SubscribeNudge subscribe, StoryForgeProperties properties,
                         @Value("${storyforge.idents.enabled:true}") boolean enabled) {
        this.renderer = renderer;
        this.media = media;
        this.bank = bank;
        this.music = music;
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.subscribe = subscribe;
        this.cache = Path.of(properties.dataDir()).toAbsolutePath().resolve("library").resolve("ident");
        this.enabled = enabled;
    }

    /** On, and the channel logo is there. */
    public boolean available() {
        return enabled && Files.isRegularFile(subscribe.logo());
    }

    /**
     * The final video: content [0, introAt), the intro, the rest of the content, the outro. introAt 0 puts the
     * intro first. Each part is encoded on its own with the same settings (MPEG-TS, no B-frames), the hook and the
     * rest at the same time, then they are joined by stream copy (seconds). One FFmpeg concat filter over all the
     * parts stalled: it reads every input at once and jams when one queue fills.
     */
    public Timeline wrap(Path content, Path output, double introAt, String voice) {
        Path intro = part(build("intro", voice));
        Path outro = part(build("outro", voice));
        double at = introAt > 0.05 ? introAt : 0;
        Path work = output.resolveSibling("ident-parts");
        try {
            Files.createDirectories(work);
            Path hook = work.resolve("hook.ts");
            Path rest = work.resolve("rest.ts");
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                var restJob = pool.submit(() -> encode(at > 0 ? List.of("-ss", f("%.3f", at), "-i", content.toString())
                        : List.of("-i", content.toString()), rest));
                if (at > 0) {
                    // -t after the input: as an input option FFmpeg let the audio run past it on this file.
                    pool.submit(() -> encode(List.of("-i", content.toString(), "-t", f("%.3f", at)), hook)).get();
                }
                restJob.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MediaException("Interrupted while adding the intro and outro", e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw e.getCause() instanceof RuntimeException runtime ? runtime
                        : new MediaException("Encoding a part failed: " + e.getCause(), e.getCause());
            } finally {
                pool.shutdownNow();
            }
            List<Path> order = new ArrayList<>();
            if (at > 0) {
                order.add(hook);
            }
            order.addAll(List.of(intro, rest, outro));
            Path list = work.resolve("parts.txt");
            Files.writeString(list, String.join("\n", order.stream()
                    .map(p -> "file '" + p.toAbsolutePath().toString().replace("\\", "/") + "'").toList()),
                    StandardCharsets.UTF_8);
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", list.toString(),
                    "-c", "copy", "-bsf:a", "aac_adtstoasc", "-movflags", "+faststart", output.toString()));
            if (!Files.isRegularFile(output)) {
                throw new MediaException("Adding the intro and outro wrote no video");
            }
            return new Timeline(round(at), round(ffmpeg.probe(intro).durationSec()),
                    round(ffmpeg.probe(outro).durationSec()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(work);
        }
    }

    /** A cached intro/outro as a join-ready part (same encoding as the content parts), made once. */
    private synchronized Path part(Path clip) {
        Path ts = clip.resolveSibling(clip.getFileName().toString().replaceAll("\\.mp4$", "") + ".ts");
        if (!Files.isRegularFile(ts)) {
            Path tmp = clip.resolveSibling(ts.getFileName() + ".part");
            encode(List.of("-i", clip.toString()), tmp);
            try {
                Files.move(tmp, ts, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ts;
    }

    /** One part: 1080p30 yuv420p H.264 without B-frames, AAC 48 kHz stereo, MPEG-TS (joins cleanly by copy). */
    private void encode(List<String> input, Path out) {
        List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error"));
        args.addAll(input);
        args.addAll(List.of("-vf", "fps=30,scale=1920:1080,setsar=1,format=yuv420p",
                "-af", "aformat=sample_rates=48000:channel_layouts=stereo", "-c:v", "libx264", "-preset", "veryfast",
                "-crf", "19", "-bf", "0", "-c:a", "aac", "-b:a", "192k", "-ar", "48000", "-ac", "2", "-f", "mpegts",
                out.toString()));
        ffmpeg.ffmpeg(args);
        if (!Files.isRegularFile(out)) {
            throw new MediaException("FFmpeg wrote no " + out.getFileName());
        }
    }

    /** The intro or outro for this voice, made once and cached. */
    synchronized Path build(String kind, String voice) {
        String key = kind + "-" + VERSION + "-" + hash(voice + "|" + String.join("|", kind.equals("intro")
                ? INTRO_LINES : OUTRO_LINES));
        Path out = cache.resolve(key + ".mp4");
        if (Files.isRegularFile(out)) {
            return out;
        }
        long started = System.currentTimeMillis();
        Path work = cache.resolve("work-" + key);
        try {
            Files.createDirectories(work);
            Files.copy(subscribe.logo(), work.resolve("logo.jpg"), StandardCopyOption.REPLACE_EXISTING);
            Path wav = work.resolve("voice.wav");
            List<Scene.Segment> segments = new ArrayList<>();
            List<String> lines = kind.equals("intro") ? INTRO_LINES : OUTRO_LINES;
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    segments.add(Scene.Segment.pause(350));
                }
                segments.add(Scene.Segment.text(lines.get(i), false));
            }
            double voiceSeconds = media.tts(segments, voice, 1.0, "en-us", wav);
            Map<String, Object> props = new LinkedHashMap<>();
            List<Cue> cues = new ArrayList<>();
            double lead;
            boolean bed;
            if (kind.equals("intro")) {
                lead = INTRO_LEAD;
                bed = false;
                props.put("kind", "intro");
                props.put("variant", "two_paths");
                props.put("durationInFrames", 150);
                cues.add(new Cue("whoosh", -1, 0.3, -18));
                cues.add(new Cue("whoosh_fast", -1, 1.0, -18));
                cues.add(new Cue("pop", 527522, 1.8, -14));
                cues.add(new Cue("impact", -1, 2.4, -12));
            } else {
                lead = OUTRO_LEAD;
                bed = true;
                // Sentence ends from the voice's pauses: the bang after the comment question, the click on
                // "subscribe", the fly to the end screen after "see you on the next one".
                List<Double> ends = sentenceEnds(wav, voiceSeconds);
                double bang = lead + (ends.size() >= 4 ? ends.get(1) : 4.5) + 0.2;
                double click = lead + (ends.size() >= 4 ? ends.get(2) : 7.7) - 0.3;
                double fly = lead + (ends.size() >= 4 ? ends.get(3) : voiceSeconds) + 0.6;
                double pill = Math.max(bang + 0.5, click - CLICK_AFTER);
                props.put("kind", "outro");
                props.put("variant", "paths");
                props.put("durationInFrames", (int) Math.round(fly * FPS) + 180);
                props.put("beats", Map.of("bang", frames(bang), "subscribe", frames(pill), "fly", frames(fly)));
                cues.add(new Cue("whoosh", -1, 0.35, -21));
                cues.add(new Cue("whoosh_fast", 475133, bang - 1.55, -20));
                cues.add(new Cue("riser", -1, bang, -15));
                cues.add(new Cue("boom", -1, bang, -8));
                cues.add(new Cue("impact", -1, bang + 0.02, -11));
                cues.add(new Cue("whoosh_fast", 475133, pill + 0.25, -20));
                cues.add(new Cue("click", 825193, pill + CLICK_AFTER, -13));
                cues.add(new Cue("bell", 504784, pill + CLICK_AFTER + 0.2, -16));
                cues.add(new Cue("whoosh", -1, fly + 0.05, -20));
                cues.add(new Cue("pop", 527522, fly + 0.9, -15));
            }
            Path propsFile = work.resolve("props.json");
            Files.writeString(propsFile, json.writeValueAsString(props), StandardCharsets.UTF_8);
            Path silent = work.resolve("video.mp4");
            renderer.renderIdent(propsFile, work, silent);
            Path part = cache.resolve(key + ".part.mp4");
            mix(silent, wav, lead, cues, bed, part);
            Files.move(part, out, StandardCopyOption.REPLACE_EXISTING);
            log.info("Channel {} made for voice {} in {} s", kind, voice, (System.currentTimeMillis() - started) / 1000);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deleteQuietly(work);
        }
    }

    /** Voice, sounds (loudest moment on their beat) and a quiet music bed under the silent animation. */
    private void mix(Path silent, Path wav, double lead, List<Cue> cues, boolean bed, Path out) {
        double duration = ffmpeg.probe(silent).durationSec();
        List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error", "-i", silent.toString(),
                "-i", wav.toString()));
        StringBuilder graph = new StringBuilder(f("[1:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=2dB,"
                + "adelay=%d|%d[voice]", ms(lead), ms(lead)));
        StringBuilder labels = new StringBuilder("[voice]");
        int input = 2;
        for (Cue cue : cues) {
            Optional<SoundBank.Sound> sound = cue.id() > 0 ? bank.find(cue.category(), cue.id())
                    : bank.pick(cue.category(), cue.category().hashCode());
            if (sound.isEmpty()) {
                continue;
            }
            SoundBank.Sound s = sound.get();
            double start = Math.max(0, cue.at() - s.peak());
            args.addAll(List.of("-i", s.file()));
            graph.append(f(";[%d:a]aformat=sample_rates=48000:channel_layouts=stereo,volume=%.1fdB,adelay=%d|%d[s%d]",
                    input, s.gainForPeak(cue.peakDb()), ms(start), ms(start), input));
            labels.append("[s").append(input).append(']');
            input++;
        }
        if (bed) {
            List<MusicLibrary.Track> calm = music.scan().getOrDefault("calm", List.of());
            if (!calm.isEmpty()) {
                args.addAll(List.of("-i", calm.get(0).file().toString()));
                graph.append(f(";[%d:a]aformat=sample_rates=48000:channel_layouts=stereo,atrim=0:%.3f,volume=-17dB,"
                        + "afade=t=in:d=1.2,afade=t=out:st=%.3f:d=2[bed]", input, duration, Math.max(0, duration - 2)));
                labels.append("[bed]");
                input++;
            }
        }
        int count = labels.toString().split("\\[").length - 1;
        graph.append(';').append(labels).append(f("amix=inputs=%d:normalize=0,apad,atrim=0:%.3f,"
                + "loudnorm=I=-14:TP=-1.5:LRA=11[a]", count, duration));
        args.addAll(List.of("-filter_complex", graph.toString(), "-map", "0:v", "-map", "[a]", "-c:v", "libx264",
                "-preset", "veryfast", "-crf", "18", "-pix_fmt", "yuv420p", "-r", "30", "-c:a", "aac", "-b:a", "192k",
                "-ar", "48000", out.toString()));
        ffmpeg.ffmpeg(args);
    }

    /** Where each sentence of the voice ends (the start of the pause after it), then the end of the file. */
    private List<Double> sentenceEnds(Path wav, double voiceSeconds) {
        List<Double> ends = new ArrayList<>();
        try {
            String output = ffmpeg.ffmpeg(List.of("-i", wav.toString(), "-af", "silencedetect=noise=-40dB:d=0.25",
                    "-f", "null", "-"));
            Matcher matcher = SILENCE.matcher(output);
            while (matcher.find()) {
                double at = Double.parseDouble(matcher.group(1));
                if (at < voiceSeconds - 0.2) {
                    ends.add(at);
                }
            }
        } catch (RuntimeException e) {
            log.debug("Silence detection failed: {}", e.getMessage());
        }
        ends.add(voiceSeconds);
        return ends;
    }

    private static int frames(double seconds) {
        return (int) Math.round(seconds * FPS);
    }

    private static int ms(double seconds) {
        return (int) Math.round(seconds * 1000);
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }

    private static String f(String format, Object... values) {
        return String.format(Locale.ROOT, format, values);
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 10);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException | RuntimeException e) {
            log.debug("Could not clean {}", dir);
        }
    }
}
