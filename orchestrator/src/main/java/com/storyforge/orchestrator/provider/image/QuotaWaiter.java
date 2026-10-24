package com.storyforge.orchestrator.provider.image;

import com.storyforge.orchestrator.run.RunService;
import com.storyforge.orchestrator.run.RunStatus;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.LongPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Waits for the free image quota to come back (00:00 UTC) instead of filling a video with stand-in pictures.
 * While waiting, {@code assets/waiting.json} tells the progress page until when; the run can still be cancelled.
 * If the app is closed meanwhile, the interrupted run is requeued at the next start and simply waits again.
 */
@Component
public class QuotaWaiter {

    public static final String WAITING = "waiting.json";
    private static final Logger log = LoggerFactory.getLogger(QuotaWaiter.class);
    private static final Duration MARGIN = Duration.ofMinutes(2);
    private static final Duration POLL = Duration.ofSeconds(30);

    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final Clock clock;
    private final Sleeper sleeper;
    private final LongPredicate cancelled;

    @Autowired
    public QuotaWaiter(RunService runs) {
        this(Clock.systemUTC(), duration -> Thread.sleep(duration.toMillis()),
                runId -> runs.get(runId).getStatus() != RunStatus.RUNNING);
    }

    QuotaWaiter(Clock clock, Sleeper sleeper, LongPredicate cancelled) {
        this.clock = clock;
        this.sleeper = sleeper;
        this.cancelled = cancelled;
    }

    /** The next quota reset (00:00 UTC) plus a small margin. */
    public Instant nextReset() {
        return clock.instant().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().plus(MARGIN);
    }

    public void waitForReset(StageContext context, String reason) {
        Instant until = nextReset();
        long runId = context.run().id();
        log.warn("Run {}: {}; waiting for the free picture quota until {}", runId, reason, until);
        context.files().write(Stage.ASSETS, WAITING, Map.of("until", until.toString(), "reason", reason));
        try {
            while (clock.instant().isBefore(until)) {
                if (cancelled.test(runId)) {
                    throw new IllegalStateException("Run stopped while waiting for the free picture quota");
                }
                Duration left = Duration.between(clock.instant(), until);
                sleeper.sleep(left.compareTo(POLL) < 0 ? left : POLL);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the free picture quota", e);
        } finally {
            try {
                Files.deleteIfExists(context.files().path(Stage.ASSETS, WAITING));
            } catch (IOException e) {
                log.debug("Could not remove {}: {}", WAITING, e.getMessage());
            }
        }
        log.info("Run {}: free picture quota is back, continuing", runId);
    }
}
