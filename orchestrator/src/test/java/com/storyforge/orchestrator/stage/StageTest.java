package com.storyforge.orchestrator.stage;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.run.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StageTest {

    @Test
    void stagesChainFromCreatedToLearningWithoutGaps() {
        List<Stage> visited = new ArrayList<>();
        RunState state = RunState.CREATED;
        Optional<Stage> next;
        while ((next = Stage.after(state)).isPresent()) {
            visited.add(next.get());
            state = next.get().to();
        }

        assertThat(visited).containsExactly(Stage.values());
        assertThat(state).isEqualTo(RunState.LEARNING);
    }

    @Test
    void foldersRoundTrip() {
        for (Stage stage : Stage.values()) {
            assertThat(Stage.fromFolder(stage.folder())).contains(stage);
        }
        assertThat(Stage.fromFolder("../etc")).isEmpty();
    }

    @Test
    void andLaterIncludesTheStageItself() {
        assertThat(Stage.PUBLISH.andLater()).containsExactly(Stage.PUBLISH, Stage.LEARN);
    }
}
