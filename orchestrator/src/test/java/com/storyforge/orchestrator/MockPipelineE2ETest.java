package com.storyforge.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.dto.CreateInspirationRequest;
import com.storyforge.orchestrator.dto.CreateRunRequest;
import com.storyforge.orchestrator.inspiration.Inspiration;
import com.storyforge.orchestrator.inspiration.InspirationService;
import com.storyforge.orchestrator.inspiration.InspirationStatus;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.MediaInfo;
import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.model.QaReport;
import com.storyforge.orchestrator.qa.QaService;
import com.storyforge.orchestrator.artifact.RunOverrides;
import com.storyforge.orchestrator.model.Overlay;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ScheduleSlot;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.revision.Revision;
import com.storyforge.orchestrator.revision.RevisionKind;
import com.storyforge.orchestrator.revision.RevisionService;
import com.storyforge.orchestrator.revision.RevisionStatus;
import com.storyforge.orchestrator.run.QuickStartService;
import com.storyforge.orchestrator.stage.worker.VisualPlanStage;
import tools.jackson.core.type.TypeReference;
import com.storyforge.orchestrator.settings.Preferences;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.system.CheckResult;
import com.storyforge.orchestrator.system.SystemCheckService;
import com.storyforge.orchestrator.run.PipelineEngine;
import com.storyforge.orchestrator.run.Run;
import com.storyforge.orchestrator.run.RunService;
import com.storyforge.orchestrator.run.RunState;
import com.storyforge.orchestrator.run.RunStatus;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageExecution;
import com.storyforge.orchestrator.stage.StageExecutionService;
import com.storyforge.orchestrator.stage.StageStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles({"test", "mock"})
class MockPipelineE2ETest {

    @Autowired
    private RunService runs;

    @Autowired
    private PipelineEngine engine;

    @Autowired
    private ArtifactStore artifacts;

    @Autowired
    private StageExecutionService executions;

    @Autowired
    private FfmpegService ffmpeg;

    @Autowired
    private InspirationService inspirations;

    @Autowired
    private PreferencesService preferences;

    @Autowired
    private SystemCheckService systemCheck;

    @Autowired
    private RevisionService revisions;

    @Autowired
    private QuickStartService quickStart;

    @BeforeAll
    static void cleanDataDir() throws IOException {
        Path dir = Path.of("target", "e2e-data");
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    try {
                        Files.delete(path);
                    } catch (IOException locked) {
                        System.out.println("Could not delete (locked on Windows?): " + path);
                    }
                }
            }
        }
    }

    @Test
    void mockProfileTurnsASeedTopicIntoAVideoAndAPassingQaReport() {
        long id = runFresh("The Lighthouse Keepers of Flannan Isles");

        Run run = runs.get(id);
        assertThat(run.getStatus()).as(run.getStatusReason()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getState()).isEqualTo(RunState.LEARNING);
        List<StageExecution> history = executions.forRun(id);
        assertThat(history).extracting(StageExecution::getStage).containsExactly(Stage.values());
        assertThat(history).allMatch(execution -> execution.getStatus() == StageStatus.SUCCEEDED);

        RunFiles files = artifacts.forRun(id);
        Path video = files.path(Stage.ASSEMBLE, QaService.VIDEO);
        MediaInfo info = ffmpeg.probe(video);
        assertThat(info.width()).isEqualTo(1920);
        assertThat(info.height()).isEqualTo(1080);
        assertThat(info.hasAudio()).isTrue();

        QaReport report = files.read(Stage.QA, "qa_report.json", QaReport.class);
        assertThat(report.checks()).filteredOn(check -> !check.passed())
                .as("failed checks").noneMatch(check -> check.severity() == QaReport.Severity.CRITICAL);
        assertThat(report.passed()).isTrue();
        assertThat(files.exists(Stage.QA, "qa_report.html")).isTrue();
        assertThat(files.exists(Stage.PACKAGE, "thumbnail.png")).isTrue();
        assertThat(files.readText(Stage.PACKAGE, "description.txt")).contains("0:00 The Signal Stops", "Sources");

        System.out.println("E2E video: " + video.toAbsolutePath());
        System.out.println("E2E QA report: " + files.path(Stage.QA, "qa_report.html").toAbsolutePath());
    }

    @Test
    void rerunningAStageReusesCachedLlmOutputsInsteadOfSpendingTokens() {
        long id = runFresh("Rerun cache check");

        runs.rerunFrom(id, Stage.SCRIPT, false);
        assertThat(runs.claimNextQueued()).contains(id);
        engine.execute(id);

        assertThat(runs.get(id).getStatus()).isEqualTo(RunStatus.COMPLETED);
        List<StageExecution> scriptRuns = executions.forRun(id).stream()
                .filter(execution -> execution.getStage() == Stage.SCRIPT).toList();
        assertThat(scriptRuns).hasSize(2);
        assertThat(scriptRuns.get(1).getProvider()).isEqualTo("mock:cache");
        assertThat(scriptRuns.get(1).getInputTokens()).isZero();
    }

    @Test
    void inspirationTranscriptBecomesIdeasAndPassesTheOriginalityGate() throws InterruptedException {
        Inspiration inspiration = inspirations.create(new CreateInspirationRequest(List.of(),
                "Hadi qissa dyal wahed lmarkab li tlef f lbhar. Kolchi ghber, w hta wahed ma 3ref chno wqe3."));
        for (int i = 0; i < 100 && inspirations.get(inspiration.getId()).getStatus() == InspirationStatus.PENDING; i++) {
            Thread.sleep(100);
        }
        assertThat(inspirations.get(inspiration.getId()).getStatus()).isEqualTo(InspirationStatus.READY);
        InspirationIdeas ideas = inspirations.ideas(inspiration.getId()).orElseThrow();
        assertThat(ideas.angles()).hasSize(3);

        long id = runs.create(new CreateRunRequest(null, ideas.topic(), null, null, null, inspiration.getId(),
                ideas.angles().get(1).angle(), "am_michael")).getId();
        assertThat(runs.claimNextQueued()).contains(id);
        engine.execute(id);

        assertThat(runs.get(id).getStatus()).as(runs.get(id).getStatusReason()).isEqualTo(RunStatus.COMPLETED);
        RunFiles files = artifacts.forRun(id);
        assertThat(files.exists(Stage.DISCOVER, "inspiration.json")).isTrue();
        QaReport report = files.read(Stage.QA, "qa_report.json", QaReport.class);
        assertThat(report.checks()).filteredOn(check -> check.id().equals("legal.originality"))
                .singleElement().satisfies(check -> {
                    assertThat(check.severity()).isEqualTo(QaReport.Severity.CRITICAL);
                    assertThat(check.passed()).isTrue();
                });
    }

    @Test
    void preferencesDriveNewRunDefaultsAndThePublishSlot() {
        Preferences base = preferences.get();
        try {
            preferences.save(new Preferences("bm_george", 12.5, base.defaultFormat(), false, 0, false, false, "low",
                    DayOfWeek.SUNDAY, "09:30", "Europe/London", null));
            assertThatThrownBy(() -> preferences.save(new Preferences(null, 0.2, null, false, 0, false, false,
                    "loud", null, "9h", "Mars/Olympus", null))).isInstanceOf(IllegalArgumentException.class);

            long id = runFresh("Preferences check");
            Run run = runs.get(id);
            assertThat(run.getVoice()).isEqualTo("bm_george");
            assertThat(run.getTargetSeconds()).isEqualTo(750);
            RunFiles files = artifacts.forRun(id);
            if (files.exists(Stage.SCHEDULE, "schedule.json")) {
                ScheduleSlot slot = files.read(Stage.SCHEDULE, "schedule.json", ScheduleSlot.class);
                ZonedDateTime at = slot.publishAt().atZone(ZoneId.of("Europe/London"));
                assertThat(at.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
                assertThat(at.getHour()).isEqualTo(9);
                assertThat(at.getMinute()).isEqualTo(30);
            }
        } finally {
            preferences.reset();
        }
        assertThat(preferences.saved()).isEmpty();
    }

    @Test
    void systemCheckReportsEveryDependencyWithoutThrowing() {
        List<CheckResult> checks = systemCheck.run();
        assertThat(checks).extracting(CheckResult::key)
                .contains("claude", "ffmpeg", "media-svc", "disk", "youtube", "telegram");
        assertThat(checks).filteredOn(c -> c.key().equals("ffmpeg"))
                .allSatisfy(c -> assertThat(c.level()).isEqualTo(CheckResult.Level.OK));
    }

    @Test
    void directorAddsOnlyOverlaysTheNarrationSupports() {
        long id = runFresh("Director check");
        RunFiles files = artifacts.forRun(id);
        List<Overlay> overlays = files.read(Stage.VISUAL_PLAN, VisualPlanStage.OVERLAYS,
                new TypeReference<List<Overlay>>() { });
        assertThat(overlays).extracting(Overlay::title).containsExactly("Shore");
        assertThat(overlays.get(0).subtitle()).isEqualTo("three in the morning");
        ShotList shots = files.read(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST, ShotList.class);
        assertThat(shots.shots().get(0).prompt()).contains("lighthouse beam going out");
        assertThat(files.readText(Stage.ASSEMBLE, "edl.json")).contains("\"overlays\"", "Shore");
    }

    @Test
    void ownerRevisionsRedoOnlyWhatWasPointedAt() {
        long id = runFresh("Revision check");
        revisions.add(id, RevisionKind.IMAGE, "01#1", 1.0, "make it a moonlit night, colder light");
        revisions.add(id, RevisionKind.NARRATION, "03", 20.0, "shorter and more vivid");
        revisions.add(id, RevisionKind.MUSIC, "02", null, "none");
        revisions.add(id, RevisionKind.PACKAGE, "title", null, "mention the lighthouse in the title");
        assertThatThrownBy(() -> revisions.add(id, RevisionKind.MUSIC, "02", null, "jazz"))
                .isInstanceOf(IllegalArgumentException.class);

        revisions.apply(id);
        assertThat(revisions.list(id)).extracting(Revision::getStatus).containsOnly(RevisionStatus.QUEUED);
        assertThat(runs.claimNextQueued()).contains(id);
        engine.execute(id);

        assertThat(runs.get(id).getStatus()).as(runs.get(id).getStatusReason()).isEqualTo(RunStatus.COMPLETED);
        assertThat(revisions.list(id)).extracting(Revision::getStatus).containsOnly(RevisionStatus.APPLIED);
        RunFiles files = artifacts.forRun(id);
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        assertThat(scenes.stream().filter(sc -> sc.id().equals("03")).findFirst().orElseThrow().narration())
                .startsWith("It began with an ordinary crew");
        ShotList shots = files.read(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST, ShotList.class);
        assertThat(RevisionService.byKey(shots).get("01#1").prompt()).contains("full moon");
        RunOverrides overrides = files.overrides();
        assertThat(overrides.sceneMusic()).containsEntry("02", "none");
        assertThat(overrides.packageNotes()).containsExactly("[title] mention the lighthouse in the title");
        assertThat(executions.forRun(id).stream().filter(e -> e.getStage() == Stage.SCRIPT)).hasSize(1);
    }

    @Test
    void quickStartNeedsNothingButCanTakeLinksOrATopic() throws InterruptedException {
        QuickStartService.Result fromNothing = quickStart.start(List.of(), null, null, null);
        assertThat(fromNothing.runId()).isNotNull();
        assertThat(runs.claimNextQueued()).contains(fromNothing.runId());
        engine.execute(fromNothing.runId());

        QuickStartService.Result fromText = quickStart.start(List.of(),
                "Qissa dyal wahed lmarkab li tlef f lbhar, w hta wahed ma 3ref chno wqe3.", null, "keep it eerie");
        assertThat(fromText.inspirationId()).isNotNull();
        Long started = null;
        for (int i = 0; i < 100 && started == null; i++) {
            Thread.sleep(100);
            started = runs.list().stream().filter(r -> fromText.inspirationId().equals(r.getInspirationId()))
                    .map(Run::getId).findFirst().orElse(null);
        }
        assertThat(started).as("run started by itself").isNotNull();
        assertThat(runs.get(started).getAngle()).contains("Owner notes: keep it eerie");
        assertThat(runs.claimNextQueued()).contains(started);
        engine.execute(started);
        assertThat(runs.get(started).getStatus()).isEqualTo(RunStatus.COMPLETED);
    }

    private long runFresh(String topic) {
        long id = runs.create(new CreateRunRequest(null, topic, null, null, null, null, null, null)).getId();
        assertThat(runs.claimNextQueued()).contains(id);
        engine.execute(id);
        return id;
    }
}
