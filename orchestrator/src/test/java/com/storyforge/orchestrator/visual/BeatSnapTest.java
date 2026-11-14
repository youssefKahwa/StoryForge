package com.storyforge.orchestrator.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import java.util.List;
import org.junit.jupiter.api.Test;

class BeatSnapTest {

    private static Shot shot(String id, String scene, double start, double end) {
        return new Shot(id, scene, "character", start, end, "p", "push_in", null, 1);
    }

    @Test
    void aCutInsideASceneMovesOntoANearbyBeat() {
        ShotList list = new ShotList(12, List.of(shot("a", "01", 0, 4.1), shot("b", "01", 4.1, 8), shot("c", "01", 8, 12)));
        ShotList snapped = BeatSnap.shots(list, List.of(2.0, 4.0, 6.0, 8.3, 10.0));
        assertThat(snapped.shots().get(0).end()).isEqualTo(4.0);
        assertThat(snapped.shots().get(1).start()).isEqualTo(4.0);
        assertThat(snapped.shots().get(1).end()).isEqualTo(8.0);
        assertThat(snapped.shots().get(2).start()).isEqualTo(8.0);
    }

    @Test
    void aCutBetweenScenesOrFarFromABeatStays() {
        ShotList list = new ShotList(12, List.of(shot("a", "01", 0, 4.1), shot("b", "02", 4.1, 8), shot("c", "02", 8, 12)));
        ShotList snapped = BeatSnap.shots(list, List.of(4.0, 8.5));
        assertThat(snapped.shots().get(0).end()).isEqualTo(4.1);
        assertThat(snapped.shots().get(1).end()).isEqualTo(8.0);
    }

    @Test
    void noShotGetsShorterThanASecond() {
        ShotList list = new ShotList(6, List.of(shot("a", "01", 0, 1.1), shot("b", "01", 1.1, 6)));
        assertThat(BeatSnap.shots(list, List.of(0.9)).shots().get(0).end()).isEqualTo(1.1);
    }

    @Test
    void theShareOfCutsOnTheBeatIsMeasured() {
        assertThat(BeatSnap.onBeat(List.of(1.0, 2.03, 3.5), List.of(1.0, 2.0, 3.0), 0.06)).isEqualTo(2 / 3.0);
        assertThat(BeatSnap.onBeat(List.of(1.0), List.of(), 0.06)).isZero();
    }
}
