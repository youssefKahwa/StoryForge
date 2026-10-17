package com.storyforge.orchestrator.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.VideoPackage.ScoredTitle;
import java.util.List;
import org.junit.jupiter.api.Test;

class TitleScorerTest {

    @Test
    void ranksShortCuriousKeywordTitlesFirstAndDropsDuplicates() {
        List<ScoredTitle> top = TitleScorer.top(List.of(
                "Mary Celeste",
                "Why the Mary Celeste Was Never Really Solved",
                "Why the Mary Celeste Was Never Really Solved",
                "An Extremely Long Title About A Ship That Goes On And On Well Past Sixty Characters",
                "THE GHOST SHIP"), "The Mary Celeste", 3);

        assertThat(top).hasSize(3);
        assertThat(top.get(0).title()).isEqualTo("Why the Mary Celeste Was Never Really Solved");
        assertThat(top).extracting(ScoredTitle::title).doesNotHaveDuplicates();
        assertThat(top).noneMatch(title -> title.title().startsWith("An Extremely Long"));
    }

    @Test
    void formatsChapterTimestamps() {
        assertThat(DescriptionBuilder.timestamp(0)).isEqualTo("0:00");
        assertThat(DescriptionBuilder.timestamp(75.9)).isEqualTo("1:15");
        assertThat(DescriptionBuilder.timestamp(3725)).isEqualTo("1:02:05");
    }
}
