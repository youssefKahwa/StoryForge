package com.storyforge.orchestrator.host;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SubscribeNudgeTest {

    @Test
    void threeTimesInALongVideo() {
        assertThat(SubscribeNudge.plan(600, List.of())).containsExactly(60.0, 300.0, 525.0);
    }

    @Test
    void movesAwayFromBusyMoments() {
        // The host is on screen 58-66 s: the first pop-up moves to the nearest free moment.
        List<Double> starts = SubscribeNudge.plan(600, List.of(new double[] {58, 66}));
        // Nearest free start: 67 s (one second after the host), 7 s away; before it would be 52.5 s, 7.5 s away.
        assertThat(starts.get(0)).isEqualTo(67.0);
        assertThat(starts).hasSize(3);
    }

    @Test
    void skippedWhenNothingIsFreeOrTheVideoIsShort() {
        assertThat(SubscribeNudge.plan(90, List.of())).isEmpty();
        assertThat(SubscribeNudge.plan(600, List.of(new double[] {0, 600}))).isEmpty();
    }
}
