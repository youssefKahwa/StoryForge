package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.run.PowerStatus;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.MediaException;
import com.storyforge.orchestrator.model.Edl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "render", havingValue = "remotion")
public class RemotionRenderer implements RenderProvider {

    /** A render whose log shows no progress this long is stuck (a crashed browser that never came back). */
    private static final long STALL_MS = 10 * 60 * 1000L;
    /** The fast lane's Remotion part prints progress every ~20 s; this long without any means it is stuck. */
    private static final long DESIGNED_STALL_MS = 3 * 60 * 1000L;
    private static final java.util.regex.Pattern PROGRESS = java.util.regex.Pattern.compile("progress (\\d+)%");
    /** More "tab closed" errors than this in the log tail: the browser is gone, stop instead of waiting. */
    private static final int CRASH_LINES = 20;
    private static final Logger log = LoggerFactory.getLogger(RemotionRenderer.class);
    private static final List<String> SYNCED = List.of("package.json", "render.mjs", "still.mjs", "ident.mjs", "src");
    private static final int TAIL = 3000;
    private static final long STILL_TIMEOUT_SECONDS = 300;

    private final StoryForgeProperties.RenderSvc config;
    private final Path installDir;
    private final Path sourceDir;
    private final JsonMapper json;
    private final PowerStatus power;
    private final FfmpegService ffmpeg;
    private final boolean fastLane;

    public RemotionRenderer(StoryForgeProperties properties, JsonMapper json, PowerStatus power, FfmpegService ffmpeg,
                            @org.springframework.beans.factory.annotation.Value("${storyforge.render-svc.fast-lane:true}")
                            boolean fastLane) {
        this.json = json;
        this.power = power;
        this.ffmpeg = ffmpeg;
        this.fastLane = fastLane;
        this.config = properties.renderSvc();
        this.installDir = Path.of(config.dir()).toAbsolutePath();
        this.sourceDir = Path.of(config.source()).toAbsolutePath();
    }

    private static final double GB_PER_TAB = 0.9;
    private static final int SAFE_TABS = 2;
    /**
     * The fast lane's film look, drawn into every piece: the vignette only. Moving grain is new random noise on every
     * frame, the worst case for H.264: the join took 35 minutes and the file ballooned, and YouTube's own encode
     * smears it into blocks anyway.
     */
    private static final String FILM_LOOK = "vignette=angle=PI/5,format=yuv420p";
    /** Final quality: the pieces are joined without re-encoding. */
    private static final String PIECE_CRF = "19";
    /** FFmpeg pieces drawn at the same time in the fast lane. */
    private static final int PIECE_THREADS = 2;

    /**
     * Parallel browser tabs for this render: the configured number, but no more than free memory allows (~0.9 GB a
     * tab; VS Code and Chrome often leave only 2-4 GB) and only 2 on battery, where each frame is much slower.
     */
    private int concurrency() {
        int configured = config.concurrency() > 0 ? config.concurrency() : 4;
        double freeGb = freeMemoryGb();
        int byMemory = Math.max(1, (int) Math.floor(freeGb / GB_PER_TAB));
        int tabs = Math.min(configured, byMemory);
        if (power.onBattery()) {
            tabs = Math.min(SAFE_TABS, tabs);
        }
        log.info("Rendering with {} tab(s) ({} GB free{})", tabs, String.format(java.util.Locale.ROOT, "%.1f", freeGb),
                power.onBattery() ? ", on battery: plug in for full speed" : "");
        return tabs;
    }

    private static double freeMemoryGb() {
        if (java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getFreeMemorySize() / (1024.0 * 1024 * 1024);
        }
        return 4;
    }

    @Override
    public String name() {
        return "remotion";
    }

    @Override
    public void render(Edl edl, Path runRoot, Path outputMp4) {
        syncSources();
        if (!Files.isDirectory(installDir.resolve("node_modules").resolve("@remotion"))) {
            throw new MediaException("Remotion is not installed in " + installDir
                    + ". Run scripts/render-svc.ps1 -Install once.");
        }
        int tabs = concurrency();
        if (fastLane) {
            // The GPU first; a Remotion hiccup (a crashed browser, a clip frame it could not read) gets one more fast
            // try on Chrome's default GL with fewer tabs before the slow whole-video render.
            String[][] tries = {{"angle", String.valueOf(tabs)}, {"default", String.valueOf(Math.min(tabs, SAFE_TABS))}};
            for (String[] attempt : tries) {
                try {
                    renderFastLane(edl, runRoot, outputMp4, Integer.parseInt(attempt[1]), attempt[0]);
                    return;
                } catch (MediaException | UncheckedIOException e) {
                    log.warn("Fast-lane render failed with GL {} ({})", attempt[0],
                            e.getMessage() == null ? "?" : e.getMessage().lines().findFirst().orElse("?"));
                }
            }
            log.warn("Fast lane failed twice; rendering the whole video with Remotion");
        }
        try {
            // The Intel GPU (ANGLE) renders about 12 frames/s here, 3x Chrome's default.
            renderOnce(edl, runRoot, outputMp4, tabs, "angle");
        } catch (MediaException e) {
            // A crash, out of memory or a stalled render: once more on Chrome's default GL with fewer tabs.
            log.warn("Render failed with the GPU and {} tabs ({}); rendering again with Chrome's default GL and {}",
                    tabs, e.getMessage() == null ? "?" : e.getMessage().lines().findFirst().orElse("?"),
                    Math.min(tabs, SAFE_TABS));
            renderOnce(edl, runRoot, outputMp4, Math.min(tabs, SAFE_TABS), "default");
        }
    }

    /**
     * Fast lane (see {@link FastLane}): Remotion draws only the designed ranges, FFmpeg the plain shots (several at
     * once), then one FFmpeg pass joins them, adds the film look and the soundtrack.
     */
    private void renderFastLane(Edl edl, Path runRoot, Path outputMp4, int tabs, String gl) {
        long started = System.currentTimeMillis();
        if (edl.audio() == null || edl.audio().size() != 1 || edl.audio().get(0).start() != 0
                || edl.audio().get(0).gainDb() != 0) {
            throw new MediaException("the fast lane needs the soundtrack as one finished mix");
        }
        List<FastLane.Range> ranges = FastLane.plan(edl);
        Path work = outputMp4.resolveSibling("fastlane");
        Path logFile = outputMp4.resolveSibling("render.log");
        try {
            if (Files.isDirectory(work)) {
                try (Stream<Path> old = Files.list(work)) {
                    for (Path file : old.toList()) {
                        Files.deleteIfExists(file);
                    }
                }
            }
            Files.createDirectories(work);
            Files.writeString(logFile, "Fast lane\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int width = edl.width();
        int height = edl.height();
        int fps = edl.fps();
        int total = ranges.stream().mapToInt(FastLane.Range::frames).sum();
        int designed = ranges.stream().filter(FastLane.Range::designed).mapToInt(FastLane.Range::frames).sum();
        // Work units: a designed frame costs ~6 plain ones; the final join (a copy) next to nothing.
        double units = designed * 6.0 + (total - designed) + total * 0.02;
        log.info("Fast lane: {} of {} frames designed (Remotion), the rest by FFmpeg", designed, total);
        List<int[]> designedRanges = new ArrayList<>();
        List<Path> designedFiles = new ArrayList<>();
        List<Path> order = new ArrayList<>();
        List<FastLane.Piece> plain = new ArrayList<>();
        List<Path> plainFiles = new ArrayList<>();
        for (FastLane.Range range : ranges) {
            if (range.designed()) {
                Path file = work.resolve(String.format("d_%06d.mp4", range.from()));
                designedRanges.add(new int[] {range.from(), range.to()});
                designedFiles.add(file);
                order.add(file);
            } else {
                for (FastLane.Piece piece : FastLane.pieces(edl, range)) {
                    Path file = work.resolve(String.format("p_%06d.mp4", piece.from()));
                    plain.add(piece);
                    plainFiles.add(file);
                    order.add(file);
                }
            }
        }
        Path designedFile = work.resolve("designed.mp4");
        Path designedLog = work.resolve("designed.log");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(PIECE_THREADS);
        java.util.concurrent.ScheduledExecutorService reporter =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        java.util.concurrent.atomic.AtomicInteger doneFrames = new java.util.concurrent.atomic.AtomicInteger();
        try {
            Files.writeString(designedLog, "", StandardCharsets.UTF_8);
            // FFmpeg draws the plain pieces while Remotion draws the designed ranges (the browser leaves CPU free).
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < plain.size(); i++) {
                FastLane.Piece piece = plain.get(i);
                Path file = plainFiles.get(i);
                futures.add(pool.submit(() -> {
                    plainPiece(piece, runRoot, file, width, height, fps, work);
                    doneFrames.addAndGet(piece.frames());
                }));
            }
            reporter.scheduleAtFixedRate(() -> appendProgress(logFile,
                    (designed * 6.0 * lastProgress(designedLog) + doneFrames.get()) / units), 5, 5, TimeUnit.SECONDS);
            if (!designedRanges.isEmpty()) {
                // One Remotion render of all designed ranges back to back (the "Designed" composition): a render
                // per range cost ~15 s of browser start-up each.
                Path jobs = work.resolve("ranges.json");
                Files.writeString(jobs, json.writeValueAsString(designedRanges), StandardCharsets.UTF_8);
                runNode(List.of(config.node(), installDir.resolve("render.mjs").toString(),
                        outputMp4.resolveSibling("edl.json").toString(), runRoot.toAbsolutePath().toString(),
                        designedFile.toString()),
                        Map.of("RENDER_CONCURRENCY", String.valueOf(Math.max(1, tabs - 1)), "RENDER_GL", gl, "RENDER_RANGES",
                                jobs.toString()), designedLog, designedFile, DESIGNED_STALL_MS);
            }
            int at = 0;
            for (int i = 0; i < designedRanges.size(); i++) {
                int first = at;
                int frames = designedRanges.get(i)[1] - designedRanges.get(i)[0];
                Path file = designedFiles.get(i);
                at += frames;
                futures.add(pool.submit(() -> ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i",
                        designedFile.toString(), "-vf", "trim=start_frame=" + first + ":end_frame=" + (first + frames)
                                + ",setpts=PTS-STARTPTS,scale=out_range=tv," + FILM_LOOK, "-frames:v",
                        String.valueOf(frames), "-an", "-r", String.valueOf(fps), "-c:v", "libx264", "-preset",
                        "veryfast", "-crf", PIECE_CRF, "-bf", "0", file.toString()))));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                future.get();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime
                    : new MediaException("Fast-lane piece failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted during the fast-lane render", e);
        } finally {
            reporter.shutdownNow();
            pool.shutdownNow();
        }
        appendProgress(logFile, (designed * 6.0 + total - designed) / units);
        join(edl, runRoot, order, work, outputMp4);
        appendProgress(logFile, 1.0);
        log.info("Fast lane: {} frames rendered in {} s", total, (System.currentTimeMillis() - started) / 1000);
    }

    /** One plain shot piece drawn by FFmpeg, exactly as long as its frame range. */
    private void plainPiece(FastLane.Piece piece, Path runRoot, Path out, int width, int height, int fps, Path work) {
        Edl.EdlShot shot = piece.shot();
        Path asset = runRoot.resolve(shot.asset());
        boolean video = !"ai".equals(shot.media());
        List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error"));
        if (video) {
            args.addAll(List.of("-ss", String.format(java.util.Locale.ROOT, "%.3f",
                    (piece.from() - piece.shotFrom()) / (double) fps), "-i", asset.toString()));
        } else {
            args.addAll(List.of("-loop", "1", "-framerate", String.valueOf(fps), "-i", asset.toString()));
        }
        String filter = FastLane.filter(piece, width, height, fps);
        String padded = filter.replaceFirst("^fps=" + fps + ",", "fps=" + fps + ",tpad=stop_mode=clone:stop_duration=3,");
        boolean credit = shot.credit() != null && !shot.credit().isBlank()
                && ("clip".equals(shot.media()) || "footage".equals(shot.media()));
        if (credit) {
            Path png = creditImage(shot.credit(), width, height, work);
            args.addAll(List.of("-i", png.toString(), "-filter_complex",
                    "[0:v]" + padded + "[v];[v][1:v]overlay=0:0:format=auto," + FILM_LOOK + "[o]", "-map", "[o]"));
        } else {
            args.addAll(List.of("-vf", padded + "," + FILM_LOOK));
        }
        args.addAll(List.of("-frames:v", String.valueOf(piece.frames()), "-an", "-r", String.valueOf(fps),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", PIECE_CRF, "-bf", "0", out.toString()));
        ffmpeg.ffmpeg(args);
    }

    /** The small credit line of a clip (MediaShot.jsx Credit), as a transparent full-frame PNG, made once. */
    private static synchronized Path creditImage(String text, int width, int height, Path work) {
        Path png = work.resolve("credit_" + Integer.toHexString(text.hashCode()) + ".png");
        if (Files.isRegularFile(png)) {
            return png;
        }
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 17));
        int x = 44;
        int y = height - 30 - 4;
        g.setColor(new java.awt.Color(0, 0, 0, 200));
        g.drawString(text, x + 1, y + 1);
        g.setColor(new java.awt.Color(245, 240, 230, 190));
        g.drawString(text, x, y);
        g.dispose();
        try {
            javax.imageio.ImageIO.write(image, "png", png.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return png;
    }

    /** All pieces in order (copied, no re-encode) with the soundtrack. */
    private void join(Edl edl, Path runRoot, List<Path> order, Path work, Path outputMp4) {
        Path list = work.resolve("join.txt");
        try {
            Files.writeString(list, String.join("\n", order.stream()
                    .map(p -> "file '" + p.toString().replace("\\", "/") + "'").toList()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error", "-f", "concat", "-safe", "0",
                "-i", list.toString()));
        boolean audio = edl.audio() != null && edl.audio().size() == 1;
        if (audio) {
            args.addAll(List.of("-i", runRoot.resolve(edl.audio().get(0).file()).toString()));
        }
        args.addAll(List.of("-map", "0:v:0"));
        if (audio) {
            args.addAll(List.of("-map", "1:a:0", "-c:a", "aac", "-b:a", "192k", "-shortest"));
        }
        // Every piece is encoded the same way and without B-frames: B-frames start a file at a negative DTS and
        // the concat demuxer then left gaps (12 s too long over 200 pieces, the voice out of sync). Re-encoding
        // the whole film here took ~12 minutes; the look is already in the pieces, so this is a copy.
        args.addAll(List.of("-c:v", "copy", "-movflags", "+faststart", outputMp4.toString()));
        ffmpeg.ffmpeg(args);
        if (!Files.isRegularFile(outputMp4)) {
            throw new MediaException("Fast-lane join wrote no video");
        }
    }

    /** The last "progress N%" of a Remotion log, as 0..1. */
    private static double lastProgress(Path log) {
        try {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0; i--) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("progress (\\d+)%").matcher(lines.get(i));
                if (m.find()) {
                    return Math.min(1, Integer.parseInt(m.group(1)) / 100.0);
                }
                if (lines.get(i).contains("Ranges done")) {
                    return 1;
                }
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
        return 0;
    }

    private static synchronized void appendProgress(Path logFile, double fraction) {
        try {
            Files.writeString(logFile, "progress " + (int) Math.min(100, Math.floor(fraction * 100)) + "%\n",
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.debug("Progress not written: {}", e.getMessage());
        }
    }

    /** The channel intro or outro (ident.mjs, ChannelIdent.jsx), silent; props: kind, variant, durationInFrames, beats. */
    public void renderIdent(Path propsJson, Path assetDir, Path outputMp4) {
        syncSources();
        Path logFile = outputMp4.resolveSibling("ident.log");
        try {
            Files.writeString(logFile, "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        runNode(List.of(config.node(), installDir.resolve("ident.mjs").toString(), propsJson.toString(),
                assetDir.toString(), outputMp4.toString()),
                Map.of("RENDER_CONCURRENCY", String.valueOf(concurrency()), "RENDER_GL", "angle"), logFile, outputMp4);
    }

    /** Runs render.mjs with the stall watchdog; {@code expected}: the file it must write (null: checked by caller). */
    private void runNode(List<String> command, Map<String, String> env, Path logFile, Path expected) {
        runNode(command, env, logFile, expected, STALL_MS);
    }

    private void runNode(List<String> command, Map<String, String> env, Path logFile, Path expected, long stallMs) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(installDir.toFile())
                    .redirectErrorStream(true).redirectOutput(java.lang.ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            builder.environment().putAll(env);
            Process process = builder.start();
            long deadline = System.currentTimeMillis() + config.timeoutSeconds() * 1000L;
            // Stuck = the progress number has not moved for stallMs (a crashed browser tab can keep writing errors,
            // so a growing log is no proof of life), or Chrome keeps reporting closed tabs.
            String lastProgress = "";
            long progressedAt = System.currentTimeMillis();
            while (!process.waitFor(15, TimeUnit.SECONDS)) {
                String tail = tail(logFile);
                java.util.regex.Matcher m = PROGRESS.matcher(tail);
                String progress = "";
                while (m.find()) {
                    progress = m.group(1);
                }
                if (!progress.equals(lastProgress)) {
                    lastProgress = progress;
                    progressedAt = System.currentTimeMillis();
                }
                long quiet = System.currentTimeMillis() - progressedAt;
                int crashed = tail.split("Session closed|Target closed", -1).length - 1;
                if (quiet > stallMs || crashed > CRASH_LINES || System.currentTimeMillis() > deadline) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    throw new MediaException(crashed > CRASH_LINES ? "Remotion browser tabs crashed (low memory?)"
                            : quiet > stallMs ? "Remotion render stalled" : "Remotion render timed out");
                }
            }
            if (process.exitValue() != 0 || expected != null && !Files.isRegularFile(expected)) {
                String output = Files.readString(logFile, StandardCharsets.UTF_8);
                throw new MediaException("Remotion render failed (exit " + process.exitValue() + "): "
                        + output.substring(Math.max(0, output.length() - TAIL)));
            }
        } catch (IOException e) {
            throw new MediaException("Cannot start node for Remotion (is Node.js on PATH?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted during Remotion render", e);
        }
    }

    /** The last 64 KB of a log. */
    private static String tail(Path logFile) {
        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(logFile.toFile(), "r")) {
            long length = file.length();
            int size = (int) Math.min(length, 64 * 1024);
            byte[] bytes = new byte[size];
            file.seek(length - size);
            file.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void renderOnce(Edl edl, Path runRoot, Path outputMp4, int tabs, String gl) {
        Path edlFile = outputMp4.resolveSibling("edl.json");
        Path logFile = outputMp4.resolveSibling("render.log");
        List<String> command = new ArrayList<>(List.of(config.node(), installDir.resolve("render.mjs").toString(),
                edlFile.toString(), runRoot.toAbsolutePath().toString(), outputMp4.toAbsolutePath().toString()));
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(installDir.toFile())
                    .redirectErrorStream(true).redirectOutput(logFile.toFile());
            builder.environment().put("RENDER_CONCURRENCY", String.valueOf(tabs));
            builder.environment().put("RENDER_GL", gl);
            log.info("Remotion render started: {} frames ({} GL)", Math.round(edl.durationSec() * edl.fps()), gl);
            Process process = builder.start();
            long deadline = System.currentTimeMillis() + config.timeoutSeconds() * 1000L;
            while (!process.waitFor(15, TimeUnit.SECONDS)) {
                long quiet = System.currentTimeMillis() - Files.getLastModifiedTime(logFile).toMillis();
                if (quiet > STALL_MS || System.currentTimeMillis() > deadline) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    throw new MediaException(quiet > STALL_MS
                            ? "Remotion render stalled (no progress for " + STALL_MS / 60000 + " min)"
                            : "Remotion render timed out after " + config.timeoutSeconds() + "s");
                }
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(outputMp4)) {
                String output = Files.readString(logFile, StandardCharsets.UTF_8);
                throw new MediaException("Remotion render failed (exit " + process.exitValue() + "): "
                        + output.substring(Math.max(0, output.length() - TAIL)));
            }
        } catch (IOException e) {
            throw new MediaException("Cannot start node for Remotion (is Node.js on PATH?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted during Remotion render", e);
        }
    }

    @Override
    public boolean supportsShorts() {
        return true;
    }

    @Override
    public void renderShort(Map<String, Object> spec, Path runRoot, Path outputMp4) {
        syncSources();
        Path specFile = outputMp4.resolveSibling(outputMp4.getFileName() + ".json");
        Path logFile = outputMp4.resolveSibling(outputMp4.getFileName() + ".log");
        try {
            Files.writeString(specFile, json.writeValueAsString(spec), StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder(config.node(), installDir.resolve("render.mjs").toString(),
                    specFile.toString(), runRoot.toAbsolutePath().toString(), outputMp4.toAbsolutePath().toString(),
                    "Short").directory(installDir.toFile()).redirectErrorStream(true).redirectOutput(logFile.toFile());
            builder.environment().put("RENDER_CONCURRENCY", String.valueOf(concurrency()));
            Process process = builder.start();
            if (!process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaException("Short render timed out");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(outputMp4)) {
                String output = Files.readString(logFile, StandardCharsets.UTF_8);
                throw new MediaException("Short render failed: " + output.substring(Math.max(0, output.length() - TAIL)));
            }
        } catch (IOException e) {
            throw new MediaException("Cannot render Short with Remotion", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted during Short render", e);
        }
    }

    @Override
    public void renderThumbnail(Path background, String text, List<String> palette, Path outputPng) {
        syncSources();
        Path props = outputPng.resolveSibling(outputPng.getFileName() + ".props.json");
        Path logFile = outputPng.resolveSibling(outputPng.getFileName() + ".log");
        try {
            Files.writeString(props, json.writeValueAsString(Map.of(
                    "background", background == null ? "" : background.toAbsolutePath().toString(),
                    "text", text, "palette", palette == null ? List.of() : palette)), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(config.node(), installDir.resolve("still.mjs").toString(),
                    props.toString(), outputPng.toAbsolutePath().toString())
                    .directory(installDir.toFile()).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
            if (!process.waitFor(STILL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaException("Thumbnail render timed out");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(outputPng)) {
                throw new MediaException("Thumbnail render failed: " + Files.readString(logFile, StandardCharsets.UTF_8));
            }
            Files.deleteIfExists(props);
            Files.deleteIfExists(logFile);
        } catch (IOException e) {
            throw new MediaException("Cannot render thumbnail with Remotion", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted during thumbnail render", e);
        }
    }

    private void syncSources() {
        if (!Files.isDirectory(sourceDir)) {
            return;
        }
        for (String entry : SYNCED) {
            Path from = sourceDir.resolve(entry);
            if (!Files.exists(from)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(from)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    Path target = installDir.resolve(sourceDir.relativize(file).toString());
                    if (!Files.exists(target) || Files.getLastModifiedTime(target)
                            .compareTo(Files.getLastModifiedTime(file)) < 0) {
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
