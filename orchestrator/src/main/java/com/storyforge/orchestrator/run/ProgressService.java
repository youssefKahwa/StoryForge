package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.provider.image.QuotaWaiter;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageExecution;
import com.storyforge.orchestrator.stage.StageExecutionService;
import com.storyforge.orchestrator.stage.StageStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Live progress of a run for the dashboard: overall percent, the current step in plain words with its own
 * progress (clips recorded, images found, render percent), elapsed time and an estimate of what is left. Estimates
 * come from this PC's own past runs (median, scaled by video length), with sensible defaults until there is history.
 */
@Service
public class ProgressService {

    public record StepProgress(Stage stage, String label, String status, Long seconds, long estimateSeconds) {
    }

    public record RunProgress(long runId, RunStatus status, int percent, Stage stage, String stageLabel,
                              String stageDetail, Integer stagePercent, long elapsedSeconds, Long remainingSeconds,
                              Instant expectedAt, boolean onBattery, List<StepProgress> steps) {
    }

    private static final Map<Stage, String> LABELS = labels();
    /** Seconds of work per second of finished video, for stages that grow with the video length. */
    private static final Map<Stage, Double> DEFAULT_RATE = Map.of(
            Stage.NARRATE, 0.8, Stage.ASSETS, 0.9, Stage.AUDIO_MIX, 0.15, Stage.ASSEMBLE, 3.3, Stage.PACKAGE, 1.2);
    private static final Map<Stage, Long> DEFAULT_SECONDS = Map.ofEntries(
            Map.entry(Stage.DISCOVER, 1L), Map.entry(Stage.SELECT, 30L), Map.entry(Stage.RESEARCH, 240L),
            Map.entry(Stage.OUTLINE, 90L), Map.entry(Stage.SCRIPT, 150L), Map.entry(Stage.CRITIQUE, 240L),
            Map.entry(Stage.ALIGN, 2L), Map.entry(Stage.VISUAL_PLAN, 120L), Map.entry(Stage.QA, 60L),
            Map.entry(Stage.SCHEDULE, 1L), Map.entry(Stage.PUBLISH, 20L), Map.entry(Stage.LEARN, 1L));
    private static final Set<Stage> SLOW_ON_BATTERY = Set.of(Stage.ASSETS, Stage.ASSEMBLE, Stage.PACKAGE);
    private static final double BATTERY_FACTOR = 3;
    private static final Pattern RENDER_PROGRESS = Pattern.compile("progress (\\d+)%");
    private static final Pattern SHOT_FILE = Pattern.compile("(shot_\\d+)(\\.clip)?\\.(png|jpg|mp4)");

    private final RunService runs;
    private final RunRepository repository;
    private final StageExecutionService executions;
    private final ArtifactStore artifacts;
    private final PowerStatus power;
    private final PreferencesService preferences;

    public ProgressService(RunService runs, RunRepository repository, StageExecutionService executions,
                           ArtifactStore artifacts, PowerStatus power, PreferencesService preferences) {
        this.runs = runs;
        this.repository = repository;
        this.executions = executions;
        this.artifacts = artifacts;
        this.power = power;
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public RunProgress progress(long runId) {
        Run run = runs.get(runId);
        List<StageExecution> history = executions.forRun(runId);
        Map<Stage, Long> estimates = estimates(run);
        boolean battery = power.onBattery();
        Stage current = currentStage(run);
        long total = estimates.values().stream().mapToLong(Long::longValue).sum();
        double doneWeight = 0;
        long remaining = 0;
        String detail = null;
        Integer stagePercent = null;
        List<StepProgress> steps = new ArrayList<>();
        Instant now = Instant.now();
        for (Stage stage : Stage.values()) {
            long estimate = estimates.get(stage);
            if (battery && SLOW_ON_BATTERY.contains(stage)) {
                estimate = Math.round(estimate * BATTERY_FACTOR);
            }
            StageExecution last = latest(history, stage);
            boolean done = isDone(run, stage);
            if (done) {
                doneWeight += estimates.get(stage);
                steps.add(new StepProgress(stage, LABELS.get(stage), "done", seconds(last), estimate));
            } else if (stage == current && run.getStatus() == RunStatus.RUNNING) {
                long elapsed = last != null && last.getStartedAt() != null && last.getFinishedAt() == null
                        ? Duration.between(last.getStartedAt(), now).toSeconds() : 0;
                Inner inner = inner(run, stage);
                double fraction = inner.fraction() != null ? inner.fraction()
                        : Math.min(0.95, estimate == 0 ? 0 : (double) elapsed / estimate);
                detail = inner.detail();
                stagePercent = inner.fraction() == null ? null : (int) Math.round(inner.fraction() * 100);
                long left = inner.waitUntil() != null
                        ? Math.max(0, Duration.between(now, inner.waitUntil()).toSeconds()) + estimate
                        : inner.fraction() != null && inner.fraction() > 0.03 && elapsed > 20
                        ? Math.round(elapsed / inner.fraction() - elapsed)
                        : Math.max(estimate - elapsed, Math.round(estimate * 0.05));
                remaining += left;
                doneWeight += estimates.get(stage) * fraction;
                steps.add(new StepProgress(stage, LABELS.get(stage), "running", elapsed, estimate));
            } else {
                String status = last != null && last.getStatus() == StageStatus.FAILED && stage == current
                        ? "failed" : "pending";
                remaining += estimate;
                steps.add(new StepProgress(stage, LABELS.get(stage), status, null, estimate));
            }
        }
        boolean finished = run.getStatus() == RunStatus.COMPLETED;
        int percent = finished ? 100 : (int) Math.min(99, Math.floor(doneWeight * 100 / Math.max(1, total)));
        Instant started = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedAt();
        Instant end = run.getFinishedAt() != null ? run.getFinishedAt() : now;
        long elapsedRun = started == null ? 0 : Duration.between(started, end).toSeconds();
        boolean active = run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.QUEUED;
        Long left = active ? remaining : null;
        return new RunProgress(runId, run.getStatus(), percent, finished ? null : current,
                finished ? "Done" : LABELS.get(current), detail, stagePercent, elapsedRun, left,
                left == null ? null : now.plusSeconds(left), battery, steps);
    }

    /** waitUntil: the stage is paused until then (free picture quota), so that time is added to what is left. */
    private record Inner(Double fraction, String detail, Instant waitUntil) {

        Inner(Double fraction, String detail) {
            this(fraction, detail, null);
        }
    }

    /** Progress inside the current stage, read from the files it writes. */
    private Inner inner(Run run, Stage stage) {
        RunFiles files = artifacts.forRun(run.getId());
        try {
            switch (stage) {
                case NARRATE -> {
                    int scenes = files.exists(Stage.CRITIQUE, "scenes.json") ? sceneCount(files) : 0;
                    long clips = list(dir(files, Stage.NARRATE)).filter(f -> f.matches("scene_.+\\.wav")).count();
                    if (scenes > 0) {
                        return new Inner(Math.min(1.0, (double) clips / scenes),
                                "Recording scene " + Math.min(clips + 1, scenes) + " of " + scenes);
                    }
                }
                case ASSETS -> {
                    if (files.exists(Stage.ASSETS, QuotaWaiter.WAITING)) {
                        Instant until = Instant.parse(files.read(Stage.ASSETS, QuotaWaiter.WAITING, Map.class)
                                .get("until").toString());
                        String at = until.atZone(ZoneId.systemDefault()).toLocalTime()
                                .truncatedTo(ChronoUnit.MINUTES).toString();
                        return new Inner(null, "Waiting for the free picture quota (resets daily): continues at "
                                + at + ", keep the app open", until);
                    }
                    if (files.exists(Stage.VISUAL_PLAN, "shotlist.json")) {
                        int shots = files.read(Stage.VISUAL_PLAN, "shotlist.json", ShotList.class).shots().size();
                        Set<String> made = new HashSet<>();
                        list(dir(files, Stage.ASSETS)).forEach(name -> {
                            Matcher m = SHOT_FILE.matcher(name);
                            if (m.matches()) {
                                made.add(m.group(1));
                            }
                        });
                        return new Inner(Math.min(1.0, (double) made.size() / Math.max(1, shots)),
                                "Picture " + Math.min(made.size() + 1, shots) + " of " + shots);
                    }
                }
                case ASSEMBLE -> {
                    Path log = dir(files, Stage.ASSEMBLE).resolve("render.log");
                    if (Files.isRegularFile(log)) {
                        Matcher m = RENDER_PROGRESS.matcher(Files.readString(log));
                        int pct = -1;
                        while (m.find()) {
                            pct = Integer.parseInt(m.group(1));
                        }
                        if (pct >= 0) {
                            return new Inner(pct / 100.0, "Rendering " + pct + "%");
                        }
                        return new Inner(0.0, "Preparing the render");
                    }
                }
                case PACKAGE -> {
                    int shorts = preferences.get().shortsEnabled() ? preferences.get().shortsCount() : 0;
                    long rendered = list(dir(files, Stage.PACKAGE)).filter(f -> f.matches("short_\\d+\\.mp4")).count();
                    if (shorts > 0) {
                        return new Inner(Math.min(0.95, 0.2 + 0.8 * rendered / shorts),
                                "Short " + Math.min(rendered + 1, shorts) + " of " + shorts);
                    }
                }
                default -> {
                    return new Inner(null, null);
                }
            }
        } catch (IOException | RuntimeException e) {
            return new Inner(null, null);
        }
        return new Inner(null, null);
    }

    /** Median past duration per stage, scaled by video length where the work grows with it. */
    private Map<Stage, Long> estimates(Run current) {
        Map<Stage, List<Double>> rates = new EnumMap<>(Stage.class);
        Map<Stage, List<Double>> durations = new EnumMap<>(Stage.class);
        List<Run> past = repository.findByStatus(RunStatus.COMPLETED).stream()
                .filter(r -> r.getProfile() == null || !r.getProfile().contains("mock")).toList();
        Map<Long, Run> byId = new java.util.HashMap<>();
        past.forEach(r -> byId.put(r.getId(), r));
        for (StageExecution e : executions.forRuns(byId.keySet())) {
            if (e.getStatus() != StageStatus.SUCCEEDED || e.getDurationMs() == null
                    || (e.getProvider() != null && e.getProvider().contains(":cache"))) {
                continue;
            }
            double seconds = e.getDurationMs() / 1000.0;
            Run r = byId.get(e.getRunId());
            if (DEFAULT_RATE.containsKey(e.getStage()) && r.getTargetSeconds() > 0) {
                rates.computeIfAbsent(e.getStage(), k -> new ArrayList<>()).add(seconds / r.getTargetSeconds());
            } else {
                durations.computeIfAbsent(e.getStage(), k -> new ArrayList<>()).add(seconds);
            }
        }
        Map<Stage, Long> out = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            if (DEFAULT_RATE.containsKey(stage)) {
                double rate = median(rates.get(stage), DEFAULT_RATE.get(stage));
                out.put(stage, Math.round(rate * current.getTargetSeconds()));
            } else {
                out.put(stage, Math.round(median(durations.get(stage), DEFAULT_SECONDS.getOrDefault(stage, 30L))));
            }
        }
        return out;
    }

    private static Stage currentStage(Run run) {
        for (Stage stage : Stage.values()) {
            if (stage.from() == run.getState()) {
                return stage;
            }
        }
        return Stage.LEARN;
    }

    private static boolean isDone(Run run, Stage stage) {
        return run.getStatus() == RunStatus.COMPLETED || stage.to().ordinal() <= run.getState().ordinal();
    }

    private static StageExecution latest(List<StageExecution> history, Stage stage) {
        StageExecution found = null;
        for (StageExecution e : history) {
            if (e.getStage() == stage) {
                found = e;
            }
        }
        return found;
    }

    private static Long seconds(StageExecution e) {
        return e == null || e.getDurationMs() == null ? null : Math.round(e.getDurationMs() / 1000.0);
    }

    private static double median(List<Double> values, double fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        List<Double> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    /** Read-only path of a stage folder (RunFiles.dir would create it). */
    private static Path dir(RunFiles files, Stage stage) {
        return files.root().resolve(stage.folder());
    }

    private static int sceneCount(RunFiles files) {
        return files.read(Stage.CRITIQUE, "scenes.json", List.class).size();
    }

    private static Stream<String> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Stream.empty();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.map(p -> p.getFileName().toString()).toList().stream();
        }
    }

    private static Map<Stage, String> labels() {
        Map<Stage, String> labels = new EnumMap<>(Stage.class);
        labels.put(Stage.DISCOVER, "Getting the topic ready");
        labels.put(Stage.SELECT, "Choosing the angle");
        labels.put(Stage.RESEARCH, "Researching sources");
        labels.put(Stage.OUTLINE, "Planning the chapters");
        labels.put(Stage.SCRIPT, "Writing the script");
        labels.put(Stage.CRITIQUE, "Reviewing and polishing the script");
        labels.put(Stage.NARRATE, "Recording the narration");
        labels.put(Stage.ALIGN, "Syncing words to the voice");
        labels.put(Stage.VISUAL_PLAN, "Planning every shot");
        labels.put(Stage.ASSETS, "Finding photos, footage and pictures");
        labels.put(Stage.AUDIO_MIX, "Mixing music and sound");
        labels.put(Stage.ASSEMBLE, "Rendering the video");
        labels.put(Stage.QA, "Quality checks");
        labels.put(Stage.PACKAGE, "Title, thumbnails and Shorts");
        labels.put(Stage.SCHEDULE, "Picking the publish time");
        labels.put(Stage.PUBLISH, "Preparing the publish kit");
        labels.put(Stage.LEARN, "Wrapping up");
        return labels;
    }
}
