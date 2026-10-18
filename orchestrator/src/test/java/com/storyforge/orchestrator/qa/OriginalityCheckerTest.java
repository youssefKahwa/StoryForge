package com.storyforge.orchestrator.qa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OriginalityCheckerTest {

    private static final String SOURCE = "In 1872 the crew vanished without a trace and nobody ever found out why the "
            + "lifeboat was missing from the deck that cold December morning";

    @Test
    void flagsCopiedPassages() {
        String script = "Our story starts here. The crew vanished without a trace and nobody ever found out why the "
                + "lifeboat was missing. Then we move on.";

        OriginalityChecker.Result result = OriginalityChecker.compare(script, List.of(SOURCE));

        assertThat(result.longestSharedRun()).isGreaterThanOrEqualTo(14);
        assertThat(result.example()).startsWith("the crew vanished");
        assertThat(result.original(12, 0.05)).isFalse();
    }

    @Test
    void acceptsTheSameFactsInOurOwnWords() {
        String script = "December 1872. Ten people disappear from a ship that is still afloat. One small boat is gone, "
                + "and the question of where they went has never been answered.";

        OriginalityChecker.Result result = OriginalityChecker.compare(script, List.of(SOURCE));

        assertThat(result.longestSharedRun()).isLessThan(5);
        assertThat(result.original(12, 0.05)).isTrue();
    }
}
