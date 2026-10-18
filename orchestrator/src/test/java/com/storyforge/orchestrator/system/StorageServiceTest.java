package com.storyforge.orchestrator.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.storyforge.orchestrator.TestProperties;
import com.storyforge.orchestrator.run.Run;
import com.storyforge.orchestrator.run.RunRepository;
import com.storyforge.orchestrator.run.RunStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageServiceTest {

    @TempDir
    Path data;

    @Test
    void cleanupDeletesMediaOfOldCompletedRunsAndKeepsJsonAndExports() throws IOException {
        Path oldRun = data.resolve("runs/1");
        Path newRun = data.resolve("runs/2");
        write(oldRun.resolve("08-narrate/narration.wav"), 4000);
        write(oldRun.resolve("09-assets/s01.png"), 3000);
        write(oldRun.resolve("04-script/script.json"), 100);
        write(newRun.resolve("08-narrate/narration.wav"), 5000);
        write(data.resolve("exports/2026-10-01-video/video.mp4"), 6000);

        List<Run> completed = List.of(run(1, Instant.now().minus(Duration.ofDays(30))),
                run(2, Instant.now().minus(Duration.ofDays(2))));
        RunRepository repository = mock(RunRepository.class);
        when(repository.findByStatus(RunStatus.COMPLETED)).thenReturn(completed);
        StorageService storage = new StorageService(TestProperties.withDataDir(data.toString()), repository);

        StorageService.Usage usage = storage.usage(14);
        assertThat(usage.cleanableRuns()).isEqualTo(1);
        assertThat(usage.cleanableBytes()).isEqualTo(7000);
        assertThat(usage.exportsBytes()).isEqualTo(6000);

        StorageService.Cleanup cleanup = storage.cleanup(14);
        assertThat(cleanup.runsCleaned()).isEqualTo(1);
        assertThat(cleanup.bytesFreed()).isEqualTo(7000);
        assertThat(oldRun.resolve("08-narrate/narration.wav")).doesNotExist();
        assertThat(oldRun.resolve("04-script/script.json")).exists();
        assertThat(newRun.resolve("08-narrate/narration.wav")).exists();
        assertThat(data.resolve("exports/2026-10-01-video/video.mp4")).exists();
        assertThat(storage.usage(14).cleanableRuns()).isZero();
    }

    private static Run run(long id, Instant finished) {
        Run run = mock(Run.class);
        when(run.getId()).thenReturn(id);
        when(run.getFinishedAt()).thenReturn(finished);
        return run;
    }

    private static void write(Path file, int bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[bytes]);
    }
}
