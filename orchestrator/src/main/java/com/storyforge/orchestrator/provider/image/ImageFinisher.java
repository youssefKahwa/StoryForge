package com.storyforge.orchestrator.provider.image;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ImageFinisher {

    private static final Logger log = LoggerFactory.getLogger(ImageFinisher.class);
    private static final long UPSCALE_TIMEOUT_SECONDS = 180;

    private final FfmpegService ffmpeg;
    private final StoryForgeProperties.Upscaler config;
    private volatile boolean upscalerBroken;

    public ImageFinisher(FfmpegService ffmpeg, StoryForgeProperties properties) {
        this.ffmpeg = ffmpeg;
        this.config = properties.upscaler();
    }

    public void finish(Path source, Path output, int width, int height) {
        Path cropped = output.resolveSibling(output.getFileName() + ".crop.png");
        Path upscaled = output.resolveSibling(output.getFileName() + ".up.png");
        try {
            String aspect = String.format(Locale.ROOT, "%d/%d", width, height);
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", source.toString(), "-vf",
                    "crop='min(iw,ih*" + aspect + ")':'min(ih,iw/(" + aspect + "))'", cropped.toString()));
            Path input = upscale(cropped, upscaled) ? upscaled : cropped;
            String sharpen = input == upscaled ? "" : ",unsharp=5:5:0.6:5:5:0";
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", input.toString(), "-vf",
                    "scale=" + width + ":" + height + ":flags=lanczos" + sharpen, output.toString()));
        } finally {
            deleteQuietly(cropped);
            deleteQuietly(upscaled);
        }
    }

    private boolean upscale(Path input, Path output) {
        if (!config.enabled() || upscalerBroken) {
            return false;
        }
        Path exe = Path.of(config.executable());
        if (!Files.isRegularFile(exe)) {
            return false;
        }
        try {
            Process process = new ProcessBuilder(exe.toString(), "-i", input.toString(), "-o", output.toString(),
                    "-n", config.model(), "-s", String.valueOf(config.scale()))
                    .directory(exe.getParent().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(UPSCALE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Upscaler timed out; falling back to Lanczos for the rest of this session");
                upscalerBroken = true;
                return false;
            }
            return process.exitValue() == 0 && Files.isRegularFile(output);
        } catch (IOException e) {
            log.warn("Upscaler unavailable ({}); using Lanczos", e.getMessage());
            upscalerBroken = true;
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void copyCached(Path cached, Path output) {
        try {
            Files.copy(cached, output, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // temp file cleanup is best effort
        }
    }
}
