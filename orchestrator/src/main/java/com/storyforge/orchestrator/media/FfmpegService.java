package com.storyforge.orchestrator.media;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class FfmpegService {

    private static final Pattern LOUDNORM_JSON = Pattern.compile("\\{[^{}]*\"input_i\"[^{}]*}", Pattern.DOTALL);
    private static final Pattern BLACK = Pattern.compile("black_start:([0-9.]+)\\s+black_end:([0-9.]+)");
    private static final Pattern FREEZE_START = Pattern.compile("freeze_start:\\s*([0-9.]+)");
    private static final Pattern FREEZE_END = Pattern.compile("freeze_end:\\s*([0-9.]+)");
    private static final int TAIL = 3000;
    private static final int MAX_LOUDNESS_PASSES = 4;
    private static final double LOUDNESS_TOLERANCE = 0.3;
    private static final double LIMIT_MARGIN_DB = 0.5;
    private static final double LIMITER_COMPENSATION = 1.3;

    private final String ffmpeg;
    private final String ffprobe;
    private final long timeoutSeconds;
    private final JsonMapper json;

    public FfmpegService(StoryForgeProperties properties, JsonMapper json) {
        this.ffmpeg = properties.media().ffmpeg();
        this.ffprobe = properties.media().ffprobe();
        this.timeoutSeconds = properties.media().timeoutSeconds();
        this.json = json;
    }

    public String ffmpeg(List<String> args) {
        List<String> command = new ArrayList<>();
        command.add(ffmpeg);
        command.add("-hide_banner");
        command.add("-nostdin");
        command.addAll(args);
        return run(command);
    }

    public MediaInfo probe(Path file) {
        String output = run(List.of(ffprobe, "-v", "error", "-print_format", "json", "-show_format", "-show_streams",
                file.toString()));
        JsonNode root = json.readTree(output);
        JsonNode video = null;
        JsonNode audio = null;
        for (JsonNode stream : root.path("streams")) {
            String type = stream.path("codec_type").asString("");
            if (video == null && type.equals("video")) {
                video = stream;
            } else if (audio == null && type.equals("audio")) {
                audio = stream;
            }
        }
        double duration = root.path("format").path("duration").asDouble(0);
        return new MediaInfo(
                video == null ? 0 : video.path("width").asInt(0),
                video == null ? 0 : video.path("height").asInt(0),
                video == null ? 0 : frameRate(video.path("avg_frame_rate").asString("0/1")),
                duration,
                video == null ? 0 : video.path("duration").asDouble(duration),
                audio == null ? 0 : audio.path("duration").asDouble(duration),
                video != null,
                audio != null,
                video == null ? null : video.path("codec_name").asString(null),
                audio == null ? null : audio.path("codec_name").asString(null));
    }

    public LoudnessStats measureLoudness(Path file) {
        String output = ffmpeg(List.of("-i", file.toString(), "-vn",
                "-af", "loudnorm=I=-14:TP=-1:LRA=11:print_format=json", "-f", "null", "-"));
        JsonNode node = loudnormJson(output, file);
        return new LoudnessStats(number(node, "input_i"), number(node, "input_tp"), number(node, "input_lra"));
    }

    public LoudnessStats normalizeLoudness(Path input, Path output, double targetLufs, double truePeakDb,
                                           int sampleRate) {
        double limit = Math.pow(10, (truePeakDb - LIMIT_MARGIN_DB) / 20);
        double gain = targetLufs - measureLoudness(input).integratedLufs();
        LoudnessStats result = null;
        for (int pass = 0; pass < MAX_LOUDNESS_PASSES; pass++) {
            ffmpeg(List.of("-y", "-i", input.toString(),
                    "-af", String.format(Locale.ROOT, "volume=%.2fdB,alimiter=limit=%.3f:attack=1:release=50:level=false",
                            gain, limit),
                    "-ar", String.valueOf(sampleRate), "-ac", "2", output.toString()));
            result = measureLoudness(output);
            double error = targetLufs - result.integratedLufs();
            if (Math.abs(error) <= LOUDNESS_TOLERANCE) {
                break;
            }
            gain += error * LIMITER_COMPENSATION;
        }
        return result;
    }

    private JsonNode loudnormJson(String output, Path file) {
        Matcher matcher = LOUDNORM_JSON.matcher(output);
        String block = null;
        while (matcher.find()) {
            block = matcher.group();
        }
        if (block == null) {
            throw new MediaException("loudnorm produced no measurement for " + file);
        }
        return json.readTree(block);
    }

    public List<double[]> detectBlack(Path video, double minSeconds) {
        String output = ffmpeg(List.of("-i", video.toString(), "-an",
                "-vf", "blackdetect=d=%s:pix_th=0.10".formatted(minSeconds), "-f", "null", "-"));
        List<double[]> segments = new ArrayList<>();
        Matcher matcher = BLACK.matcher(output);
        while (matcher.find()) {
            segments.add(new double[]{Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2))});
        }
        return segments;
    }

    public List<double[]> detectFreeze(Path video, double minSeconds) {
        String output = ffmpeg(List.of("-i", video.toString(), "-an",
                "-vf", "freezedetect=n=0.001:d=%s".formatted(minSeconds), "-f", "null", "-"));
        List<double[]> segments = new ArrayList<>();
        Matcher starts = FREEZE_START.matcher(output);
        Matcher ends = FREEZE_END.matcher(output);
        while (starts.find()) {
            double start = Double.parseDouble(starts.group(1));
            double end = ends.find() ? Double.parseDouble(ends.group(1)) : -1;
            segments.add(new double[]{start, end});
        }
        return segments;
    }

    private String run(List<String> command) {
        Path log = null;
        try {
            log = Files.createTempFile("storyforge-ffmpeg", ".log");
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            if (!com.storyforge.orchestrator.media.Processes.waitFor(process, timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaException("Timed out after " + timeoutSeconds + "s: " + command.get(0));
            }
            String output = Files.readString(log, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new MediaException(command.get(0) + " exited with " + process.exitValue() + ": "
                        + output.substring(Math.max(0, output.length() - TAIL)));
            }
            return output;
        } catch (IOException e) {
            throw new MediaException("Cannot run " + command.get(0) + " (is it installed and on PATH?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted while running " + command.get(0), e);
        } finally {
            if (log != null) {
                try {
                    Files.deleteIfExists(log);
                } catch (IOException ignored) {
                    // temp file cleanup is best effort
                }
            }
        }
    }

    private static double frameRate(String ratio) {
        String[] parts = ratio.split("/");
        double numerator = Double.parseDouble(parts[0]);
        double denominator = parts.length > 1 ? Double.parseDouble(parts[1]) : 1;
        return denominator == 0 ? 0 : numerator / denominator;
    }

    private static double number(JsonNode node, String field) {
        String value = node.path(field).asString("");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.NEGATIVE_INFINITY;
        }
    }
}
