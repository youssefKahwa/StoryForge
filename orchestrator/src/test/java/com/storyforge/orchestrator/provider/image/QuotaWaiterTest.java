package com.storyforge.orchestrator.provider.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyforge.orchestrator.TestProperties;
import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.format.VideoFormat;
import com.storyforge.orchestrator.run.RunInfo;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageUsage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class QuotaWaiterTest {

    @TempDir
    Path data;

    /** A clock that moves forward by whatever the waiter sleeps. */
    private static final class FakeClock extends Clock {
        Instant now;

        FakeClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void waitsUntilJustAfterMidnightUtcAndShowsItOnTheProgressPage() {
        FakeClock clock = new FakeClock(Instant.parse("2026-10-20T22:30:00Z"));
        List<Duration> naps = new ArrayList<>();
        RunFiles files = files();
        QuotaWaiter waiter = new QuotaWaiter(clock, duration -> {
            if (naps.isEmpty()) {
                assertThat(files.exists(Stage.ASSETS, QuotaWaiter.WAITING)).isTrue();
            }
            naps.add(duration);
            clock.now = clock.now.plus(duration);
        }, runId -> false);

        assertThat(waiter.nextReset()).isEqualTo(Instant.parse("2026-10-21T00:02:00Z"));
        waiter.waitForReset(context(files), "test");

        assertThat(clock.now).isEqualTo(Instant.parse("2026-10-21T00:02:00Z"));
        assertThat(naps).allSatisfy(nap -> assertThat(nap).isLessThanOrEqualTo(Duration.ofSeconds(30)));
        assertThat(files.exists(Stage.ASSETS, QuotaWaiter.WAITING)).isFalse();
    }

    @Test
    void stopsWaitingWhenTheOwnerCancelsTheRun() {
        FakeClock clock = new FakeClock(Instant.parse("2026-10-20T22:30:00Z"));
        RunFiles files = files();
        QuotaWaiter waiter = new QuotaWaiter(clock, duration -> clock.now = clock.now.plus(duration),
                runId -> clock.now.isAfter(Instant.parse("2026-10-20T22:31:00Z")));

        assertThatThrownBy(() -> waiter.waitForReset(context(files), "test"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("stopped");
        assertThat(files.exists(Stage.ASSETS, QuotaWaiter.WAITING)).isFalse();
    }

    private RunFiles files() {
        return new ArtifactStore(TestProperties.withDataDir(data.toString()), JsonMapper.builder().build()).forRun(7);
    }

    private static StageContext context(RunFiles files) {
        return new StageContext(new RunInfo(7, "Topic", VideoFormat.FILM_ANALYSIS, "en", 600, 0, null, null, null),
                files, new StageUsage());
    }
}
