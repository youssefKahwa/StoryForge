package com.storyforge.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.dto.CreateRunRequest;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.MediaInfo;
import com.storyforge.orchestrator.model.QaReport;
import com.storyforge.orchestrator.qa.QaService;
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

    private long runFresh(String topic) {
        long id = runs.create(new CreateRunRequest(null, topic, null, null, null)).getId();
        assertThat(runs.claimNextQueued()).contains(id);
        engine.execute(id);
        return id;
    }
}
