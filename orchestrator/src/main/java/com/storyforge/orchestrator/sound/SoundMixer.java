package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.sound.SoundPlan.MusicSegment;
import com.storyforge.orchestrator.sound.SoundPlan.SfxCue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SoundMixer {

    private static final int RATE = 48_000;
    private static final String DUCK = "sidechaincompress=threshold=0.02:ratio=8:attack=30:release=600";

    private final FfmpegService ffmpeg;

    public SoundMixer(FfmpegService ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    public void mix(Path narration, SoundPlan plan, Path output) {
        List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error", "-i", narration.toString()));
        StringBuilder graph = new StringBuilder();
        graph.append(f("[0:a]aresample=%d,aformat=channel_layouts=stereo,asplit=2[voice][key];", RATE));
        int input = 1;
        List<String> musicLabels = new ArrayList<>();
        for (MusicSegment segment : plan.music()) {
            args.addAll(List.of("-stream_loop", "-1", "-i", segment.file()));
            String label = "m" + musicLabels.size();
            double length = segment.length();
            double fade = Math.min(SoundDesigner.CROSSFADE, length / 3);
            long delay = Math.round(segment.start() * 1000);
            graph.append(f("[%d:a]aresample=%d,aformat=channel_layouts=stereo,atrim=0:%.3f,asetpts=PTS-STARTPTS,"
                            + "afade=t=in:d=%.3f,afade=t=out:st=%.3f:d=%.3f,volume=%.2fdB,adelay=%d|%d[%s];",
                    input++, RATE, length, fade, length - fade, fade, segment.gainDb(), delay, delay, label));
            musicLabels.add(label);
        }
        List<String> fxLabels = new ArrayList<>();
        for (SfxCue cue : plan.sfx()) {
            args.addAll(List.of("-i", cue.file()));
            String label = "x" + fxLabels.size();
            long delay = Math.round(cue.start() * 1000);
            graph.append(f("[%d:a]aresample=%d,aformat=channel_layouts=stereo,volume=%.2fdB,adelay=%d|%d[%s];",
                    input++, RATE, cue.gainDb(), delay, delay, label));
            fxLabels.add(label);
        }
        List<String> buses = new ArrayList<>(List.of("[voice]"));
        if (!musicLabels.isEmpty()) {
            graph.append(join(musicLabels)).append(f("amix=inputs=%d:normalize=0:duration=longest[musicraw];",
                    musicLabels.size()));
            graph.append("[musicraw][key]").append(DUCK).append("[music];");
            buses.add("[music]");
        } else {
            graph.append("[key]anullsink;");
        }
        if (!fxLabels.isEmpty()) {
            graph.append(join(fxLabels)).append(f("amix=inputs=%d:normalize=0:duration=longest[fx];",
                    fxLabels.size()));
            buses.add("[fx]");
        }
        graph.append(String.join("", buses))
                .append(f("amix=inputs=%d:normalize=0:duration=first[mix]", buses.size()));
        Path graphFile = output.resolveSibling("mix_graph.txt");
        try {
            Files.writeString(graphFile, graph.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        args.addAll(List.of("-/filter_complex", graphFile.toString(), "-map", "[mix]", "-ar", String.valueOf(RATE),
                "-ac", "2", output.toString()));
        ffmpeg.ffmpeg(args);
    }

    private static String join(List<String> labels) {
        StringBuilder out = new StringBuilder();
        labels.forEach(label -> out.append('[').append(label).append(']'));
        return out.toString();
    }

    private static String f(String format, Object... values) {
        return String.format(Locale.ROOT, format, values);
    }
}
