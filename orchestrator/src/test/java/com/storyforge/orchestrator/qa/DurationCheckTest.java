package com.storyforge.orchestrator.qa;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.QaReport.Check;
import com.storyforge.orchestrator.model.QaReport.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class DurationCheckTest {

    private static Check byId(List<Check> checks, String id) {
        return checks.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void aLongVideoPassesWhenItIsOverEightMinutesEvenIfShorterThanItsTarget() {
        List<Check> checks = QaService.durationChecks(497.6, 600, 0.10);
        assertThat(byId(checks, "content.duration").passed()).isTrue();
        Check target = byId(checks, "content.duration_target");
        assertThat(target.passed()).isFalse();
        assertThat(target.severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void aLongVideoUnderEightMinutesFails() {
        Check duration = byId(QaService.durationChecks(470, 600, 0.10), "content.duration");
        assertThat(duration.passed()).isFalse();
        assertThat(duration.severity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    void aShortTestVideoKeepsTheTolerance() {
        assertThat(QaService.durationChecks(70, 70, 0.10)).singleElement()
                .satisfies(c -> assertThat(c.passed()).isTrue());
        assertThat(QaService.durationChecks(90, 70, 0.10)).singleElement()
                .satisfies(c -> assertThat(c.passed()).isFalse());
    }
}
