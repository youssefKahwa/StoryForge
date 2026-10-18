package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlAudio;
import com.storyforge.orchestrator.model.Edl.EdlShot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "render", havingValue = "ffmpeg",
        matchIfMissing = true)
public class FfmpegSlideshowRenderer implements RenderProvider {

    private final FfmpegService ffmpeg;

    public FfmpegSlideshowRenderer(FfmpegService ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    @Override
    public String name() {
        return "ffmpeg-slideshow";
    }

    @Override
    public void render(Edl edl, Path runRoot, Path outputMp4) {
        Path list = outputMp4.resolveSibling("slideshow.ffconcat");
        writeConcatList(edl, runRoot, list);
        EdlAudio mix = edl.audio().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("EDL has no audio track"));
        String filter = "fps=%d,scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d,setsar=1,format=yuv420p"
                .formatted(edl.fps(), edl.width(), edl.height(), edl.width(), edl.height());
        ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error",
                "-f", "concat", "-safe", "0", "-i", list.toString(),
                "-i", runRoot.resolve(mix.file()).toString(),
                "-map", "0:v", "-map", "1:a",
                "-vf", filter,
                "-c:v", "libx264", "-preset", "veryfast", "-tune", "stillimage", "-crf", "23",
                "-c:a", "aac", "-b:a", "192k", "-ar", "48000",
                "-t", String.format(Locale.ROOT, "%.3f", edl.durationSec()),
                "-movflags", "+faststart",
                outputMp4.toString()));
    }

    private static void writeConcatList(Edl edl, Path runRoot, Path list) {
        StringBuilder out = new StringBuilder("ffconcat version 1.0\n");
        String last = null;
        for (EdlShot shot : edl.shots()) {
            last = quote(runRoot.resolve(shot.asset()));
            out.append("file ").append(last).append('\n')
                    .append("duration ").append(String.format(Locale.ROOT, "%.3f", shot.end() - shot.start())).append('\n');
        }
        if (last != null) {
            out.append("file ").append(last).append('\n');
        }
        try {
            Files.writeString(list, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String quote(Path file) {
        return "'" + file.toAbsolutePath().toString().replace('\\', '/').replace("'", "'\\''") + "'";
    }
}
