package com.storyforge.orchestrator.provider.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChannelIdentsTest {

    @Test
    void timesAfterTheIntroMoveByItsLength() {
        ChannelIdents.Timeline timeline = new ChannelIdents.Timeline(24.5, 5.0, 16.4);
        assertThat(timeline.shift(0)).isEqualTo(0);
        assertThat(timeline.shift(24.4)).isEqualTo(24.4);
        assertThat(timeline.shift(24.5)).isEqualTo(29.5);
        assertThat(timeline.shift(300)).isEqualTo(305);
    }

    @Test
    void anIntroAtTheStartMovesEverything() {
        ChannelIdents.Timeline timeline = new ChannelIdents.Timeline(0, 5.0, 16.4);
        assertThat(timeline.shift(0)).isEqualTo(5);
        assertThat(timeline.shift(12)).isEqualTo(17);
    }
}
