package com.storyforge.orchestrator.host;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The channel's talking logo (assets/channel). Frames are cut out once (circle crop) and cached in the library with
 * a per-frame mouth openness score, measured from dark and red pixels in the mouth area.
 */
@Component
public class HostMascot {

    public record Prepared(Path dir, int frameCount, double[] openness) {
    }

    public static final String FRAME_PATTERN = "f_%04d.png";
    private static final Logger log = LoggerFactory.getLogger(HostMascot.class);

    private final FfmpegService ffmpeg;
    private final Path video;
    private final Path cacheRoot;
    private final String crop;
    private final int size;
    private final int[] mouthBox;

    public HostMascot(FfmpegService ffmpeg, StoryForgeProperties properties,
                      @Value("${storyforge.host.video:../assets/channel/DownRightTalkingMouth.mp4}") String video,
                      @Value("${storyforge.host.crop:760:760:620:160}") String crop,
                      @Value("${storyforge.host.size:300}") int size,
                      @Value("${storyforge.host.mouth-box:110,150,80,55}") String mouthBox) {
        this.ffmpeg = ffmpeg;
        this.video = Path.of(video).toAbsolutePath().normalize();
        this.cacheRoot = Path.of(properties.dataDir()).toAbsolutePath().resolve("library").resolve("host");
        this.crop = crop;
        this.size = size;
        this.mouthBox = Stream.of(mouthBox.split(",")).mapToInt(s -> Integer.parseInt(s.strip())).toArray();
    }

    public boolean available() {
        return Files.isRegularFile(video);
    }

    public Path video() {
        return video;
    }

    public synchronized Prepared prepare() {
        try {
            String key = Long.toHexString(Files.size(video) ^ Files.getLastModifiedTime(video).toMillis())
                    + "-" + crop.replace(':', '_') + "-" + size;
            Path dir = cacheRoot.resolve(key);
            if (!Files.isRegularFile(dir.resolve("done"))) {
                Files.createDirectories(dir);
                log.info("Preparing the talking host from {}", video);
                ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", video.toString(), "-vf",
                        "crop=" + crop + ",scale=" + size + ":" + size + ":flags=lanczos", "-an",
                        dir.resolve(FRAME_PATTERN).toString()));
                Files.writeString(dir.resolve("done"), "ok");
            }
            List<Path> frames;
            try (Stream<Path> files = Files.list(dir)) {
                frames = files.filter(f -> f.getFileName().toString().matches("f_\\d{4}\\.png")).sorted().toList();
            }
            double[] openness = new double[frames.size()];
            for (int i = 0; i < frames.size(); i++) {
                openness[i] = openness(ImageIO.read(frames.get(i).toFile()));
            }
            return new Prepared(dir, frames.size(), normalize(openness));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Hard-links (or copies) the frames into the run, so the renderer serves them like any other asset. */
    public void linkInto(Prepared prepared, Path target) {
        try {
            Files.createDirectories(target);
            for (int i = 1; i <= prepared.frameCount(); i++) {
                String name = String.format(Locale.ROOT, FRAME_PATTERN, i);
                Path to = target.resolve(name);
                if (Files.exists(to)) {
                    continue;
                }
                try {
                    Files.createLink(to, prepared.dir().resolve(name));
                } catch (IOException | UnsupportedOperationException e) {
                    Files.copy(prepared.dir().resolve(name), to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loudness (dBFS) of the narration for each video frame. */
    public double[] loudnessPerFrame(Path narration, int fps, Path scratch) {
        int rate = 8000;
        Path raw = scratch.resolve("host_loudness.s16le");
        ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", narration.toString(), "-ac", "1", "-ar",
                String.valueOf(rate), "-f", "s16le", raw.toString()));
        try {
            ByteBuffer pcm = ByteBuffer.wrap(Files.readAllBytes(raw)).order(ByteOrder.LITTLE_ENDIAN);
            int samples = pcm.capacity() / 2;
            int perFrame = rate / fps;
            double[] db = new double[samples / perFrame + 1];
            for (int f = 0; f < db.length; f++) {
                double sum = 0;
                int n = 0;
                for (int s = f * perFrame; s < Math.min(samples, (f + 1) * perFrame); s++, n++) {
                    double v = pcm.getShort(s * 2) / 32768.0;
                    sum += v * v;
                }
                db[f] = n == 0 || sum == 0 ? -90 : 10 * Math.log10(sum / n);
            }
            return db;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                Files.deleteIfExists(raw);
            } catch (IOException ignored) {
                // scratch cleanup is best effort
            }
        }
    }

    private double openness(BufferedImage image) {
        int x0 = mouthBox[0];
        int y0 = mouthBox[1];
        double score = 0;
        for (int y = y0; y < Math.min(image.getHeight(), y0 + mouthBox[3]); y++) {
            for (int x = x0; x < Math.min(image.getWidth(), x0 + mouthBox[2]); x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                if (r > 150 && g < 130 && b < 130) {
                    score += 2;
                } else if (r + g + b < 150) {
                    score += 1;
                }
            }
        }
        return score;
    }

    private static double[] normalize(double[] values) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = max > min ? (values[i] - min) / (max - min) : 0;
        }
        return out;
    }
}
