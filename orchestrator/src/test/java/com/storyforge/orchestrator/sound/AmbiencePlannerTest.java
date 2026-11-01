package com.storyforge.orchestrator.sound;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Scene;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AmbiencePlannerTest {

    @Test
    void everySceneGetsABedFromWhatItShowsAndNeighboursMerge() {
        List<Scene> scenes = List.of(
                scene("01", "A man stares at his office desk under fluorescent light"),
                scene("02", "Rows of cubicles, a boss walks past"),
                scene("03", "Rain hammers a window at night"),
                scene("04", "Close-up of a bar of soap"),
                scene("05", "Close-up of a bar of soap"));
        Map<String, SceneSpan> spans = Map.of(
                "01", new SceneSpan("01", 0, 20), "02", new SceneSpan("02", 20, 40),
                "03", new SceneSpan("03", 40, 60), "04", new SceneSpan("04", 60, 64),
                "05", new SceneSpan("05", 64, 90));

        List<AmbiencePlanner.Bed> beds = AmbiencePlanner.plan(scenes, spans);

        assertThat(beds).extracting(AmbiencePlanner.Bed::category)
                .containsExactly("amb_office", "amb_rain", "amb_room");
        assertThat(beds.get(0).end()).isEqualTo(40);
        assertThat(beds.get(1).end()).isEqualTo(64);
    }

    @Test
    void scenesWithoutAPlaceGetRoomTone() {
        assertThat(AmbiencePlanner.category(scene("01", "An abstract idea about identity"))).isEqualTo("amb_room");
    }

    private static Scene scene(String id, String visual) {
        return new Scene(id, 1, "beat", 20, null, null, null, null, 1.0, 0, 0, List.of(), "narration", visual,
                List.of(), null, null, List.of(), List.of());
    }
}
